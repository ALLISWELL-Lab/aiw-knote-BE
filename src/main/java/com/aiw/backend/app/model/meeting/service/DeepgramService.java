package com.aiw.backend.app.model.meeting.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class DeepgramService {

  @Value("${deepgram.api.key}")
  private String apiKey;
  @Value("${deepgram.api.url}")
  private String apiUrl;

  private final RestTemplate restTemplate;

  public DeepgramService() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(60000);
    factory.setReadTimeout(120000);
    this.restTemplate = new RestTemplate(factory);
  }

  public String transcribeWithDiarization(byte[] fileBytes) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("audio/wav")); // 파일 형식에 맞춰 변경 가능
    headers.set("Authorization", "Token " + apiKey);

    // 화자 분리(diarize), 한국어(ko), 스마트형식(smart_format) 옵션 추가
    String url = apiUrl + "?model=nova-2&smart_format=true&diarize=true&language=ko";

    HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
      return formatDeepgramResult(response.getBody());
    } catch (Exception e) {
      log.error("Deepgram API 호출 실패", e);
      throw new RuntimeException("화자 분리 처리 중 오류 발생");
    }
  }

  // Deepgram의 복잡한 JSON을 "화자 0: 안녕하세요" 형식의 문자열로 변환
  private String formatDeepgramResult(Map<String, Object> response) {
    try {
      // 계층별로 안전하게 접근
      Map<String, Object> results = (Map<String, Object>) response.get("results");
      List<Map<String, Object>> channels = (List<Map<String, Object>>) results.get("channels");
      Map<String, Object> firstChannel = channels.get(0);
      List<Map<String, Object>> alternatives = (List<Map<String, Object>>) firstChannel.get("alternatives");
      Map<String, Object> firstAlternative = alternatives.get(0);

      // paragraphs 데이터 추출
      Map<String, Object> paragraphsData = (Map<String, Object>) firstAlternative.get("paragraphs");
      List<Map<String, Object>> paragraphsList = (List<Map<String, Object>>) paragraphsData.get("paragraphs");

      StringBuilder sb = new StringBuilder();
      for (Map<String, Object> p : paragraphsList) {
        // 화자 정보 (Deepgram은 숫자로 줌)
        Object speaker = p.get("speaker");
        // 문장(sentences)들을 합쳐서 하나의 문단 텍스트 생성
        List<Map<String, Object>> sentences = (List<Map<String, Object>>) p.get("sentences");
        String paragraphText = sentences.stream()
            .map(s -> (String) s.get("text"))
            .collect(Collectors.joining(" "));

        sb.append("화자 ").append(speaker).append(": ").append(paragraphText).append("\n\n");
      }

      return sb.length() > 0 ? sb.toString() : "분석된 텍스트가 없습니다.";

    } catch (Exception e) {
      log.error("Deepgram 파싱 상세 에러: ", e);
      // 파싱 실패 시 전체 텍스트라도 반환하도록 방어 코드 작성
      return "파싱 실패. 원문: " + response.toString();
    }
  }
}
