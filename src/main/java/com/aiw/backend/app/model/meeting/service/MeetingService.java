package com.aiw.backend.app.model.meeting.service;

import com.aiw.backend.app.controller.api.meeting.payload.CreateMeetingRecordRequest;
import com.aiw.backend.app.controller.api.meeting.payload.CreateMeetingRecordResponse;
import com.aiw.backend.app.controller.api.meeting.payload.MeetingAnalysisDetailResponse;
import com.aiw.backend.app.controller.api.meeting.payload.ShowAISummaryResponse;
import com.aiw.backend.app.controller.api.meeting.payload.ShowMeetingListResponse;
import com.aiw.backend.app.controller.api.meeting.payload.ShowSttStatusResponse;
import com.aiw.backend.app.controller.api.meeting.payload.action_item.ActionItemResponse;
import com.aiw.backend.app.model.action_item.domain.ActionItem;
import com.aiw.backend.app.model.action_item.repository.ActionItemRepository;
import com.aiw.backend.app.model.meeting.domain.Meeting;
import com.aiw.backend.app.model.meeting.dto.MeetingDTO;
import com.aiw.backend.app.model.meeting.repository.MeetingRepository;
import com.aiw.backend.app.model.project.domain.Project;
import com.aiw.backend.app.model.project.repository.ProjectRepository;
import com.aiw.backend.events.BeforeDeleteMeeting;
import com.aiw.backend.util.CustomCollectors;
import com.aiw.backend.util.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MeetingService {

  private final MeetingRepository meetingRepository;
  private final ApplicationEventPublisher publisher;

  private final ProjectRepository projectRepository;
  private final ActionItemRepository actionItemRepository;

  private final AtomicLong meetingSequence = new AtomicLong(1);
  private final Map<Long, String> meetingStatusMap = new ConcurrentHashMap<>();

  private final OpenAiService openAiService;

  public MeetingService(
      final MeetingRepository meetingRepository,
      final ApplicationEventPublisher publisher,
      final ProjectRepository projectRepository,
      final OpenAiService openAiService,
      final ActionItemRepository actionItemRepository
  ) {
    this.meetingRepository = meetingRepository;
    this.publisher = publisher;
    this.projectRepository = projectRepository;
    this.openAiService = openAiService;
    this.actionItemRepository = actionItemRepository;
  }

//  public List<ShowMeetingListResponse> getMeetingRecords() {
//    return List.of(
//        new ShowMeetingListResponse(1L, "주간 회의", "2026-03-18T14:00:00", "COMPLETED"),
//        new ShowMeetingListResponse(2L, "기획 회의", "2026-03-19T10:00:00", "PROCESSING")
//    );
//  }

  @Transactional
  public MeetingDTO create(final MeetingDTO meetingDTO) {
    final Meeting meeting = new Meeting();
    mapToEntity(meetingDTO, meeting);

    // 프로젝트 연결 로직
    Project project = projectRepository.findById(meetingDTO.getProjectId())
        .orElseThrow(() -> new NotFoundException("프로젝트를 찾을 수 없습니다."));
    meeting.setProject(project);

    //DB에 저장
    final Meeting savedMeeting = meetingRepository.save(meeting);

    //저장된 엔티티를 다시 DTO로 변환하여 반환 (ID가 채워진 상태)
    return mapToDTO(savedMeeting, new MeetingDTO());
    }
  // 회의 생성
  public CreateMeetingRecordResponse createMeeting(CreateMeetingRecordRequest request) {
    Long meetingId = meetingSequence.getAndIncrement();
    meetingStatusMap.put(meetingId, "PENDING");

    return new CreateMeetingRecordResponse(
        meetingId,
        3L,
        request.getAgenda(),
        "PENDING",
        LocalDateTime.now()
    );
  }

  // 파일 업로드 생성
  public CreateMeetingRecordResponse createMeetingByFile(MultipartFile file,
      CreateMeetingRecordRequest request) {
// 1. 회의 기본 정보 생성 및 즉시 저장
    Meeting meeting = new Meeting();
    meeting.setAgenda(request.getAgenda() != null ? request.getAgenda() : "새로운 회의");

    // 프로젝트 연결 (안전하게 orElseThrow 권장)
    Project project = projectRepository.findById(1L)
        .orElseThrow(() -> new NotFoundException("1번 프로젝트가 없습니다."));
    meeting.setProject(project);

    meeting.setStatus("PENDING");
    meeting.setActivated(true);
    meeting.setCreatedType("FILE");
    meeting.setScheduledAt(LocalDateTime.now());
    meeting.setStartedAt(LocalDateTime.now());
    meeting.setEndedAt(LocalDateTime.now());

    // DB에 즉시 반영
    Meeting savedMeeting = meetingRepository.saveAndFlush(meeting);

    // [수정 포인트] 파일이 사라지기 전에 바이트 배열로 복사
    final byte[] fileBytes;
    final String originalFilename = file.getOriginalFilename();
    try {
      fileBytes = file.getBytes();
    } catch (IOException e) {
      log.error("파일 읽기 실패", e);
      throw new RuntimeException("파일을 읽을 수 없습니다.");
    }

    // 2. 별도 스레드에 복사본(fileBytes)을 넘김
    new Thread(() -> {
      // processAiTasks 메서드도 (Long, byte[], String)을 받도록 수정해야 함
      processAiTasks(savedMeeting.getId(), fileBytes, originalFilename);
    }).start();

    return new CreateMeetingRecordResponse(
        savedMeeting.getId(),
        null,
        savedMeeting.getAgenda(),
        savedMeeting.getStatus(),
        LocalDateTime.now()
    );
  }

  @Async
  @Transactional
  public void processAiTasks(Long meetingId, byte[] fileBytes, String originalFilename) {
    try {
      log.info("AI 작업 시작 - 회의 ID: {}", meetingId);
      Meeting meeting = meetingRepository.findById(meetingId)
          .orElseThrow(() -> new RuntimeException("회의를 찾을 수 없습니다."));

      // 1. STT 호출 (Whisper)
      String transcript = openAiService.transcribe(fileBytes, originalFilename);
      meeting.setTranscript(transcript);
      meeting.setStatus("PROCESSING");
      meetingRepository.saveAndFlush(meeting);

      // 2. 요약 및 분석 호출 (GPT)
      // 이제 openAiService.summarize(transcript)는 JSON 형태의 문자열을 반환해야 합니다.
      String aiJsonResponse = openAiService.summarize(transcript);

      // JSON 파싱을 위한 ObjectMapper
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(aiJsonResponse);

      // 3. Meeting 엔티티 업데이트 (사진의 기획안 반영)
      // DB 필드명에 맞춰 적절히 매핑하세요.
      meeting.setAiSummary(root.path("summarySegments").toString()); // 메인 요약본
      // meeting.setDecisions(root.path("decisions").toString()); // 결정사항 필드가 있다면 추가
      meeting.setStatus("COMPLETED");
      meetingRepository.saveAndFlush(meeting);

      // 4. ActionItem(AI TODO) 추출 및 저장
      JsonNode actionItemsNode = root.path("actionItems");
      if (actionItemsNode.isArray()) {
        for (JsonNode node : actionItemsNode) {
          ActionItem item = new ActionItem();

          // AI가 추출한 값
          item.setTitle(node.path("title").asText("새로운 할 일"));
          item.setMemo(node.path("memo").asText("내용 없음"));

          // 도메인 제약조건(nullable=false)에 따른 필수 기본값 세팅
          item.setMeeting(meeting);
          item.setCompleted(false);
          item.setActivated(true);
          item.setIsConfirmed(true); // AI가 생성한 것이므로 일단 true 혹은 false
          item.setDueDate(LocalDateTime.now().plusDays(3)); // 기본 마감기한 3일 뒤
          item.setPhase(1L); // 기본 단계
          item.setScope("TEAM"); // 기본 범위
          item.setImage("default.png"); // 기본 이미지

          // 담당자(assigneeMember)는 nullable이므로 세팅하지 않아도 됨

          actionItemRepository.save(item);
        }
      }

      log.info("모든 데이터(요약 + ActionItems) DB 저장 완료!");

    } catch (Exception e) {
      log.error("!!! 비동기 스레드 에러 발생 !!! : ", e);
    }
  }

  // 회의 목록 조회
  public List<ShowMeetingListResponse> getMeetingRecords() {
    List<Meeting> meetings = meetingRepository.findAll();
    return meetings.stream()
        .map(m -> new ShowMeetingListResponse(
            m.getId(),
            m.getAgenda(),
            m.getCreatedAt().toString(),
            m.getStatus()
        ))
        .collect(Collectors.toList());
  }

  // 회의 요약 상세 분석 결과 조회
  public MeetingAnalysisDetailResponse getMeetingAnalysis(Long meetingId) {
    Meeting meeting = meetingRepository.findById(meetingId)
        .orElseThrow(() -> new NotFoundException("회의를 찾을 수 없습니다."));

    // List<ActionItem>으로 타입을 명시
    List<ActionItem> actionItemsList = actionItemRepository.findByMeeting(meeting);

    List<ActionItemResponse> actionItems = actionItemsList.stream()
        .map(a -> new ActionItemResponse(
            a.getId(),
            a.getTitle(),
            a.getMemo(),
            a.getCompleted() // 도메인의 필드명이 getCompleted인지 확인!
        ))
        .collect(Collectors.toList());

    // DTO 생성자 인수를 4개로 맞춰서 반환
    return new MeetingAnalysisDetailResponse(
        meeting.getId(),
        meeting.getAgenda(),
        meeting.getAiSummary(), // JSON 통째로 전달
        meeting.getTranscript(),
        actionItems
    );
  }

  // 다운로드 API
  public ResponseEntity<Resource> downloadMeetingStt(Long meetingId) {
    Meeting meeting = meetingRepository.findById(meetingId)
        .orElseThrow(() -> new NotFoundException("회의를 찾을 수 없습니다."));

    String content = meeting.getTranscript(); // DB에서 꺼내옴
    ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meeting_" + meetingId + "_stt.txt\"")
        .body(resource);
  }

  // 상태 조회
  public ShowSttStatusResponse getSttStatus(Long meetingId) {
    return switch (meetingId.intValue()) {
      case 1 -> new ShowSttStatusResponse(1L, "PENDING", 0);
      case 2 -> new ShowSttStatusResponse(2L, "PROCESSING", 60);
      case 3 -> new ShowSttStatusResponse(3L, "COMPLETED", 100);
      case 4 -> new ShowSttStatusResponse(4L, "FAILED", 0);
      default -> throw new NotFoundException();
    };
  }

  public ShowAISummaryResponse createSummary(Long meetingId) {
    // 1. DB에서 회의 정보 조회
    Meeting meeting = meetingRepository.findById(meetingId)
        .orElseThrow(() -> new NotFoundException("회의를 찾을 수 없습니다."));

    // 2. 만약 아직 요약이 없다면 (비동기 처리가 덜 끝났거나 오류난 경우)
    if (meeting.getAiSummary() == null || meeting.getAiSummary().isEmpty()) {
      // 이미 status가 COMPLETED가 아니라면 아직 처리 중임을 알림
      if (!"COMPLETED".equals(meeting.getStatus())) {
        throw new RuntimeException("AI 분석이 아직 진행 중입니다. 잠시 후 다시 시도해주세요.");
      }

      // 만약 transcript는 있는데 요약만 없는 특수 상황이라면 여기서 생성 가능
      if (meeting.getTranscript() != null) {
        String summary = openAiService.summarize(meeting.getTranscript());
        meeting.setAiSummary(summary);
        meetingRepository.save(meeting);
      }
    }

    // 3. 기존 DTO 형식에 맞춰 반환
    // (현재 ShowAISummaryResponse의 필드 구성에 따라 meeting 엔티티의 값을 매핑하세요)
    return new ShowAISummaryResponse(
        meeting.getId(),
        meeting.getAgenda(), // 회의 주제
        meeting.getCreatedAt().toString(), // 생성 시간
        List.of("참석자"), // 참석자 리스트 (엔티티에 있다면 매핑, 없으면 임시 리스트)
        List.of("결정 사항"), // 결정 사항 (필요 시 요약문에서 파싱)
        List.of(meeting.getAiSummary()) // 요약 내용
    );
  }

//  public List<ShowActionItemResponse> getActionItems(Long meetingId) {
//
//  }

  public List<MeetingDTO> findAll() {
    final List<Meeting> meetings = meetingRepository.findAll(Sort.by("id"));
    return meetings.stream()
        .map(meeting -> mapToDTO(meeting, new MeetingDTO()))
        .toList();
  }

  public MeetingDTO get(final Long id) {
    return meetingRepository.findById(id)
        .map(meeting -> mapToDTO(meeting, new MeetingDTO()))
        .orElseThrow(NotFoundException::new);
  }

//  public Long create(final MeetingDTO meetingDTO) {
//    final Meeting meeting = new Meeting();
//    mapToEntity(meetingDTO, meeting);
//    return meetingRepository.save(meeting).getId();
//  }

  public void update(final Long id, final MeetingDTO meetingDTO) {
    final Meeting meeting = meetingRepository.findById(id)
        .orElseThrow(NotFoundException::new);
    mapToEntity(meetingDTO, meeting);
    meetingRepository.save(meeting);
  }

  public void delete(final Long id) {
    final Meeting meeting = meetingRepository.findById(id)
        .orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteMeeting(id));
    meetingRepository.delete(meeting);
  }

  private MeetingDTO mapToDTO(final Meeting meeting, final MeetingDTO meetingDTO) {
    meetingDTO.setId(meeting.getId());
    meetingDTO.setAgenda(meeting.getAgenda());
    meetingDTO.setScheduledAt(meeting.getScheduledAt());
    meetingDTO.setStartedAt(meeting.getStartedAt());
    meetingDTO.setEndedAt(meeting.getEndedAt());
    meetingDTO.setStatus(meeting.getStatus());
    meetingDTO.setActivated(meeting.getActivated());
    // 추가: DB의 값을 DTO로 옮겨줌
    meetingDTO.setCreatedType(meeting.getCreatedType());
    return meetingDTO;
  }

  private Meeting mapToEntity(final MeetingDTO meetingDTO, final Meeting meeting) {
    meeting.setAgenda(meetingDTO.getAgenda());
    meeting.setScheduledAt(meetingDTO.getScheduledAt());
    meeting.setStartedAt(meetingDTO.getStartedAt());
    meeting.setEndedAt(meetingDTO.getEndedAt());
    meeting.setStatus(meetingDTO.getStatus());
    meeting.setActivated(meetingDTO.getActivated());
    // 추가: 클라이언트가 보낸 값을 엔티티에 세팅
    meeting.setCreatedType(meetingDTO.getCreatedType());
    return meeting;
  }

  public Map<Long, String> getMeetingValues() {
    return meetingRepository.findAll(Sort.by("id"))
        .stream()
        .collect(CustomCollectors.toSortedMap(Meeting::getId, Meeting::getAgenda));
  }
}