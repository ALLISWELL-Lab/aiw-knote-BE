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
import org.springframework.web.multipart.MultipartFile;

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

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    Map<String, Object> body = new HashMap<>();
    body.put("model", "gpt-4o-mini");
    body.put("messages", List.of(
        Map.of("role", "system", "content",
            "너는 회의 분석 전문가야. 제공된 회의 스크립트를 분석해서 반드시 아래 JSON 형식으로만 응답해.\n" +
            "{\n" +
                "  \"title\": \"회의의 핵심 내용을 담은 한 줄 제목\",\n" +
                "  \"decisions\": [\"결정사항1\", \"결정사항2\"],\n" +
                "  \"summarySegments\": [\"요약 문장1\", \"요약 문장2\", \"요약 문장3\"],\n" +
                "  \"actionItems\": [\n" +
                "    {\"title\": \"할 일 제목\", \"memo\": \"상세 내용\"}\n" +
                "  ]\n" +
                "}")
        ,
        Map.of("role", "user", "content", transcript)
    ));

    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);

    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
    return (String) message.get("content");

  }
}

