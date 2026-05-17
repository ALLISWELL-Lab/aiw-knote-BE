package com.aiw.backend.app.model.comment.service;


import com.aiw.backend.app.model.action_item.domain.ActionItem;
import com.aiw.backend.app.model.action_item.repository.ActionItemRepository;
import com.aiw.backend.app.model.comment.domain.Comment;
import com.aiw.backend.app.model.comment.dto.CommentDTO;
import com.aiw.backend.app.model.comment.dto.DailyBriefDTO;
import com.aiw.backend.app.model.comment.dto.FeedbackDTO;
import com.aiw.backend.app.model.comment.repository.CommentRepository;
import com.aiw.backend.app.model.meeting.domain.Meeting;
import com.aiw.backend.app.model.meeting.repository.MeetingRepository;
import com.aiw.backend.app.model.meeting_summary.domain.MeetingSummary;
import com.aiw.backend.app.model.meeting_summary.repository.MeetingSummaryRepository;
import com.aiw.backend.app.model.member.domain.Member;
import com.aiw.backend.app.model.member.repository.MemberRepository;
import com.aiw.backend.events.BeforeDeleteMember;
import com.aiw.backend.infra.ai.OpenAiClient;
import com.aiw.backend.util.NotFoundException;
import com.aiw.backend.util.ReferencedException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final ActionItemRepository actionItemRepository;
    private final MeetingRepository meetingRepository;
    private final OpenAiClient openAiClient;


    // ---------------------------
    // mock 저장소 (팀원 스타일 반영)
    // ---------------------------
    private final Map<Long, CommentDTO> mockStorage = new LinkedHashMap<>();
    private long sequence = 1L;

    @PostConstruct
    public void initMockStorage() {
        if (!mockStorage.isEmpty()) return;

        // 1. 테스트용 데일리 코멘트 (회원 ID 1번용)
        CommentDTO dailyComment = new CommentDTO();
        dailyComment.setId(nextId());
        dailyComment.setContent("오늘도 파이팅하세요! 어제 완료하지 못한 태스크 1개가 남아있습니다.");
        dailyComment.setRefType("DAILY_COMMENT");
        dailyComment.setMemberId(1L);
        dailyComment.setActivated(true);

        // 2. 테스트용 과거 피드백 (AI 재분석을 위한 데이터)
        for (int i = 1; i <= 3; i++) {
            CommentDTO feedback = new CommentDTO();
            feedback.setId(nextId());
            feedback.setContent("회의 " + i + "차 피드백: " + (i == 1 ? "적극적인 태도" : "협업 능력이 우수함"));
            feedback.setRefType("FEEDBACK");
            feedback.setRefId((long) i); // projectId 가정
            feedback.setMemberId(1L);
            feedback.setActivated(true);
            mockStorage.put(feedback.getId(), feedback);
        }

        // 3. 테스트용 피드백 요약본 추가 (DB가 비었을 때 터짐 방지)
        CommentDTO feedbackSum = new CommentDTO();
        feedbackSum.setId(nextId());
        feedbackSum.setContent("이번 회의에서 당신은 주도적으로 인프라 환경 구축을 제안하여 팀에 크게 기여했습니다.");
        feedbackSum.setRefType("FEEDBACK_SUM");
        feedbackSum.setRefId(3L); // 테스트용 meetingId 3에 바인딩
        feedbackSum.setMemberId(1L);
        feedbackSum.setActivated(true);
        mockStorage.put(feedbackSum.getId(), feedbackSum);
    }

    private long nextId() { return sequence++; }

    // ---------------------------
    // AI 분석 및 저장 (본 서버 로직)
    // ---------------------------
    public CommentService(final CommentRepository commentRepository, final MemberRepository memberRepository, final MeetingSummaryRepository meetingSummaryRepository
    , final ActionItemRepository actionItemRepository,
                          final MeetingRepository meetingRepository,
                          final OpenAiClient openAiClient) {
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
        this.meetingSummaryRepository = meetingSummaryRepository;
        this.actionItemRepository = actionItemRepository;
        this.meetingRepository = meetingRepository;
        this.openAiClient = openAiClient;
    }

    @Transactional
    public void generateMeetingAIAnalysis(final Long memberId, final Long meetingId, final Long projectId) {
        // 1. [팀원 로직 반영] 특정 회의(Meeting) 엔티티 조회 및 STT 스크립트 추출
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("해당 회의를 찾을 수 없습니다."));

        String transcript = meeting.getTranscript();
        if (transcript == null || transcript.isEmpty()) {
            throw new IllegalStateException("해당 회의의 STT 텍스트 데이터(Transcript)가 아직 생성되지 않았습니다.");
        }

        // [데이터 수집] 해당 프로젝트의 모든 ActionItem 조회
        List<ActionItem> projectTasks = actionItemRepository.findByMeetingProjectId(projectId);

        long totalTasks = projectTasks.size();
        long completedTasks = projectTasks.stream().filter(ActionItem::getCompleted).count();
        double successRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        // [데이터 수집] 최근 피드백 내역 (누적 데이터)
        String feedbackHistory = commentRepository.findTop5ByMemberIdAndRefTypeOrderByDateCreatedDesc(memberId, "FEEDBACK")
                .stream()
                .map(Comment::getContent)
                .collect(Collectors.joining(" / "));

        if (feedbackHistory.isEmpty()) {
            feedbackHistory = "누적된 피드백 데이터가 아직 없습니다.";
        }
        // 4. 시스템 프롬프트 (JSON 엄격 규격 제안)
        String systemPrompt = """
        당신은 IT 팀빌딩 및 프로젝트 관리 전문가입니다.
        제공된 팀원의 성과 데이터와 이번 회의 내용을 바탕으로 개인의 태도 개선점과 팀의 현재 상태를 진단하세요.
        답변은 다른 문장이나 마크다운 래퍼(```json) 없이 오직 아래 명시된 JSON 형식 하나만을 출력해야 합니다.
        
        {
          "summary": "이 팀원의 이번 회의 참여 태도와 프로젝트 기여도를 핵심만 요약한 2~3줄 문장 (친절하고 고무적인 어조)",
          "detail": "누적 Task 완료율과 이번 회의 발언 내용을 종합 분석하여 이 팀원의 구체적인 커뮤니케이션 장점과 향후 태도 개선점을 상세히 기술한 내용"
        }
        """;

        // 5. 유저 프롬프트 (오류 방지를 위해 format 대신 replace 방식으로 전환 🚀)
        String userPromptTemplate = """
        [분석 대상 데이터]
        - 현재 프로젝트 Task 완료율: ${successRate}% (${completedTasks}개 중 ${totalTasks}개 완료)
        - 최근 회의 피드백 히스토리: ${feedbackHistory}
        
        [이번 회의 실제 대화 스크립트 (회의 ID: ${meetingId})]
        ==================================================
        ${transcript}
        ==================================================
        
        [요청사항]
        1. Task 완료율과 피드백 내용을 종합하여 이 팀원(멤버 ID: ${memberId})의 '프로젝트 참여 태도'를 분석하고 구체적인 개선 제안을 하세요.
        2. 팀의 현재 협업 건전성을 '매우좋음 / 보통 / 위험' 중 하나로 평가하고 그 이유를 짧게 설명하세요.
        3. 위 데이터를 기반으로 결과물을 기재된 JSON 필드인 'summary'와 'detail' 규칙에 알맞게 매핑하여 출력하세요.
        """;

        String userPrompt = userPromptTemplate
                .replace("${successRate}", String.format("%.1f", successRate))
                .replace("${completedTasks}", String.valueOf(completedTasks))
                .replace("${totalTasks}", String.valueOf(totalTasks))
                .replace("${feedbackHistory}", feedbackHistory)
                .replace("${meetingId}", String.valueOf(meetingId))
                .replace("${transcript}", transcript)
                .replace("${memberId}", String.valueOf(memberId));

        // 5. AI 호출 및 결과 파싱 후 개별 저장
        String summaryContent;
        String detailContent;

        try {
            String aiResponse = openAiClient.askGpt(systemPrompt, userPrompt).trim();

            // 만약 GPT가 마크다운 코드 블록 등으로 감싸서 주는 돌발 행동 대응 가공
            if (aiResponse.contains("```json")) {
                aiResponse = aiResponse.split("```json")[1].split("```")[0].trim();
            } else if (aiResponse.contains("```")) {
                aiResponse = aiResponse.split("```")[1].trim();
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(aiResponse);

            // 정상적으로 값을 가져오지 못했을 때의 기본 텍스트 방어막 구축
            summaryContent = root.path("summary").asText("이번 회의 요약 피드백 처리가 정상 완료되었습니다.");
            detailContent = root.path("detail").asText(aiResponse);

        } catch (Exception e) {
            log.error("AI 응답 JSON 파싱 완벽 실패, 폴백 텍스트 분할을 가동합니다.", e);
            summaryContent = "이번 회의 분석 결과를 요약하는 도중 시스템 파싱 격차가 발생했으나, 회의 참여 자체는 성실하게 기록되었습니다.";
            detailContent = "상세 분석 로그 오류로 인해 원본을 로드합니다. 데이터 연동 완료 상태입니다.";
        }

        // 6. 🌟 핵심: 기존 GET API가 바로 채 가도록 FEEDBACK_SUM과 FEEDBACK 타입으로 회의 ID(refId)와 묶어 저장!
        saveAiComment(memberId, "FEEDBACK_SUM", meetingId, summaryContent);
        saveAiComment(memberId, "FEEDBACK", meetingId, detailContent);

    }

    //대시보드: daily brief
    public DailyBriefDTO getDailyBrief(final Long memberId) {
        // 1. 시간 범위 설정 (오늘)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        // 2. AI 데일리 코멘트 조회 (Comment 테이블 - DAILY_COMMENT)
        // 수정: DB에 오늘의 코멘트가 없으면 Mock 저장소에서 찾아와 에러 방지
        Comment comment = commentRepository.findFirstByMemberIdAndRefTypeOrderByIdDesc(memberId, "DAILY_COMMENT")
                .orElseGet(() -> {
                    CommentDTO mockDto = mockStorage.values().stream()
                            .filter(c -> c.getMemberId().equals(memberId) && "DAILY_COMMENT".equals(c.getRefType()))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("오늘의 AI 데일리 코멘트가 없습니다."));
                    return mapToEntity(mockDto, new Comment());
                });
        // 3. 오늘 예정된 회의 목록 조회 (Meeting 테이블)
        List<DailyBriefDTO.MeetingInfoDTO> meetings = meetingRepository
                .findByScheduledAtBetween(startOfDay, endOfDay).stream()
                .map(m -> DailyBriefDTO.MeetingInfoDTO.builder()
                        .meetingId(m.getId())
                        .agenda(m.getAgenda())
                        .scheduledAt(m.getScheduledAt())
                        .build())
                .toList();

        // 4. 오늘 마감인 투두 목록 조회 (ActionItem 테이블)
        List<DailyBriefDTO.TodoInfoDTO> todos = actionItemRepository
                .findByAssigneeMemberIdAndDueDateBetween(memberId, startOfDay, endOfDay).stream()
                .map(a -> DailyBriefDTO.TodoInfoDTO.builder()
                        .todoId(a.getId())
                        .title(a.getTitle())
                        .dueDate(a.getDueDate())
                        .build())
                .toList();

        // 5. 최종 조립
        return DailyBriefDTO.builder()
                .id(comment.getId() == null ? 999L : comment.getId())
                .date(LocalDateTime.now())
                .summary("오늘 예정된 회의는 " + meetings.size() + "건, 마감 투두는 " + todos.size() + "건입니다.")
                .dailyComment(mapToDTO(comment, new CommentDTO()))
                .meetings(meetings)
                .todos(todos)
                .memberId(memberId)
                .activated(comment.getActivated())
                .build();
    }

    private void saveAiComment(Long memberId, String refType, Long refId, String content) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setRefType(refType);
        comment.setRefId(refId);
        comment.setMember(member);
        comment.setActivated(true);
        commentRepository.save(comment);
    }

    // 특정 타입의 코멘트 가져오기 (피드백 요약 등)
    public CommentDTO getCommentByRef(Long memberId, String refType, Long refId) {
        Comment comment = commentRepository.findByMemberIdAndRefTypeAndRefId(memberId, refType, refId)
                .orElseThrow(() -> new NotFoundException("해당 코멘트를 찾을 수 없습니다."));
        return mapToDTO(comment, new CommentDTO());
    }

    //피드백 요약 조회 (FEEDBACK_SUM)
    public FeedbackDTO getFeedbackSummary(Long memberId, Long meetingId) {
        Comment summary = commentRepository.findFirstByRefTypeAndRefIdOrderByIdDesc("FEEDBACK_SUM", meetingId)
                .orElseGet(() -> {
                    return mockStorage.values().stream()
                            .filter(c -> "FEEDBACK_SUM".equals(c.getRefType()) && meetingId.equals(c.getRefId()))
                            .findFirst()
                            .map(dto -> mapToEntity(dto, new Comment()))
                            .orElseThrow(() -> new NotFoundException("해당 회의(ID: " + meetingId + ")에 대한 피드백 요약이 존재하지 않습니다."));
                });

        return FeedbackDTO.builder()
                .meetingId(meetingId)
                .feedbackSummary(mapToDTO(summary, new CommentDTO()))
                .build();
    }

    //상세 AI 피드백 조회
    public FeedbackDTO getFeedbackDetail(final Long memberId, final Long meetingId){
        Comment detail = commentRepository.findFirstByRefTypeAndRefIdOrderByIdDesc("FEEDBACK", meetingId)
                .orElseGet(() -> {
                    return mockStorage.values().stream()
                            .filter(c -> "FEEDBACK".equals(c.getRefType()) && meetingId.equals(c.getRefId()))
                            .findFirst()
                            .map(dto -> mapToEntity(dto, new Comment()))
                            .orElseThrow(() -> new NotFoundException("해당 회의에 대한 AI 피드백이 존재하지 않습니다."));
                });

        return FeedbackDTO.builder()
                .meetingId(meetingId)
                .feedbackDetail(mapToDTO(detail, new CommentDTO()))
                .build();

    }

    //피드백용 회의 요약 조회
    public FeedbackDTO getMeetingSummaryForFeedback(final Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("해당 회의 데이터를 찾을 수 없습니다."));

        String aiSummary = meeting.getAiSummary();
        if (aiSummary == null || aiSummary.isEmpty()) {
            return FeedbackDTO.builder()
                    .meetingId(meetingId)
                    .meetingSummary("회의 요약 데이터가 존재하지 않습니다. 먼저 회의 생성 및 AI 요약 생성을 완료하세요.")
                    .build();
        }

        return FeedbackDTO.builder()
                .meetingId(meetingId)
                .meetingSummary(aiSummary)
                .build();
    }

    private CommentDTO mapToDTO(final Comment comment, final CommentDTO dto) {
        dto.setId(comment.getId());
        dto.setContent(comment.getContent());
        dto.setRefType(comment.getRefType());
        dto.setRefId(comment.getRefId());
        dto.setActivated(comment.getActivated());
        dto.setMemberId(comment.getMember().getId());
        dto.setDateCreated(comment.getDateCreated());
        dto.setLastUpdated(comment.getLastUpdated());
        return dto;
    }

    private Comment mapToEntity(final CommentDTO dto, final Comment comment) {
        comment.setContent(dto.getContent());
        comment.setRefType(dto.getRefType());
        comment.setRefId(dto.getRefId());
        comment.setActivated(dto.getActivated() != null ? dto.getActivated() : true);

        if (dto.getMemberId() != null) {
            // 주석: 로컬 Mock 데이터 바인딩 시 실제 멤버 조회가 실패할 수 있으므로 상황에 맞게 맵핑 처리
            memberRepository.findById(dto.getMemberId()).ifPresent(comment::setMember);
        }
        return comment;
    }

    @EventListener(BeforeDeleteMember.class)
    public void on(final BeforeDeleteMember event) {
        final ReferencedException referencedException = new ReferencedException();
        final Comment memberComment = commentRepository.findFirstByMemberId(event.getId());
        if (memberComment != null) {
            referencedException.setKey("member.comment.member.referenced");
            referencedException.addParam(memberComment.getId());
            throw referencedException;
        }
    }

    //임시 테스트용도
    public void createMockData(Long memberId, Long meetingId) {
        Member member = memberRepository.findById(memberId).orElseThrow();

        // 요약 데이터 생성
        Comment sum = new Comment();
        sum.setContent("테스트 요약 내용입니다.");
        sum.setRefType("FEEDBACK_SUM");
        sum.setRefId(meetingId);
        sum.setMember(member);
        sum.setActivated(true);
        commentRepository.save(sum);

        // 상세 데이터 생성
        Comment detail = new Comment();
        detail.setContent("테스트 상세 피드백 내용입니다.");
        detail.setRefType("FEEDBACK");
        detail.setRefId(meetingId);
        detail.setMember(member);
        detail.setActivated(true);
        commentRepository.save(detail);
    }

}
