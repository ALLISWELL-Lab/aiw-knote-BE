package com.aiw.backend.app.model.meeting.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class OpenAiService {

  @Value("${openai.api.url}")
  private String baseUrl;
  @Value("${openai.api.key}")
  private String apiKey;

  private final RestTemplate restTemplate;

  public OpenAiService() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(60000); // 60초
    factory.setReadTimeout(120000);    // 120초 (2분)
    this.restTemplate = new RestTemplate(factory);
  }

  // Whisper STT 호출
  public String transcribe(byte[] fileBytes, String originalFilename) {
    String whisperUrl = baseUrl + "/audio/transcriptions";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.setBearerAuth(apiKey);

    // byte[]를 리소스로 변환
    ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
      @Override
      public String getFilename() {
        return originalFilename; // 파일명이 없으면 OpenAI가 거절하므로 필수!
      }
    };

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", fileResource);
    body.add("model", "whisper-1");
    body.add("language", "ko");

    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    Map<String, Object> response = restTemplate.postForObject(whisperUrl, requestEntity, Map.class);
    return (String) response.get("text");
  }

  // ChatGPT 요약 호출
  public String summarize(String transcript) {
    String url = baseUrl + "/chat/completions";

    // 1. 위에서 정의한 고도화된 프롬프트
    String systemPrompt =
        "너는 전문 회의 기록가이자 프로젝트 매니저야. 제공된 회의 스크립트를 분석해서 'Action Items(할 일)'를 추출해.\n\n" +
            "**지침:**\n" +
            "1. **사실 근거:** 오직 회의 스크립트에서 언급된 할 일만 추출해. 추측하거나 새로운 일을 만들지 마.\n" +
            "2. **마감 기한(dueDate) 추천:** 대화 맥락상 언급된 기한이 있다면 그것을 사용하고, 없다면 업무의 시급성을 판단해 추천해줘.\n" +
            "   - 오늘 회의 날짜 기준으로 추천하되, 형식은 'YYYY-MM-DDTHH:mm:ss'로 고정해.\n" +
            "3. **화자 매칭:** 해당 할 일을 하기로 했거나 언급한 화자를 'suggestedSpeaker' 필드에 정확히 적어줘.\n" +
            "4. **형식:** 반드시 아래 JSON 형식으로만 응답해.\n\n" +
            "{\n" +
            "  \"title\": \"회의 제목\",\n" +
            "  \"summarySegments\": [\"요약 문장1\", \"요약 문장2\"],\n" +
            "  \"actionItems\": [\n" +
            "    {\n" +
            "      \"title\": \"할 일 제목\",\n" +
            "      \"memo\": \"상세 내용\",\n" +
            "      \"suggestedSpeaker\": \"SPEAKER_0\",\n" +
            "      \"dueDate\": \"2026-05-14T18:00:00\"\n" +
            "    }\n" +
            "  ]\n" +
            "}"
            + "5. **Phase 분류:** 각 할 일의 성격에 따라 아래 기준을 참고하여 'phase' 필드에 1~5 사이의 숫자를 부여해.\n"
            + "   - 1 (Ideation): 아이디어 회의, 요구사항 정리 관련\n"
            + "   - 2 (Design): 설계, UI/UX, DB 모델링 관련\n"
            + "   - 3 (Development): 실제 코드 작성, API 구현 관련\n"
            + "   - 4 (Testing): 버그 수정, 테스트, 리팩토링 관련\n"
            + "   - 5 (Launch): 배포, 문서화, 최종 발표 관련";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    // 2. body 구성 시 'content' 부분에 위에서 만든 systemPrompt 변수 넣기
    Map<String, Object> body = new HashMap<>();
    body.put("model", "gpt-4o-mini");
    body.put("messages", List.of(
        Map.of("role", "system", "content", systemPrompt), // 여기서 변수 적용!
        Map.of("role", "user", "content", transcript)
    ));

    // 3. JSON 모드로 응답받기 위한 설정 (옵션: 모델이 JSON 형식을 더 잘 지키게 함)
    body.put("response_format", Map.of("type", "json_object"));

    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

    try {
      Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
      List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
      Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
      return (String) message.get("content");
    } catch (Exception e) {
      log.error("GPT 요약 호출 중 에러 발생: ", e);
      return "{\"error\": \"요약 생성 실패\"}";
    }
  }
}


