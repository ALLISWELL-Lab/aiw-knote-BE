package com.aiw.backend.app.controller.api.comment;

import com.aiw.backend.app.model.comment.dto.FeedbackDTO;
import com.aiw.backend.app.model.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/comments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "Comment", description = "AI 피드백 및 코멘트 조회 API")
public class CommentController {
    private final CommentService commentService;

    // 회의 AI 요약 생성처럼 피드백을 빌드해내는 POST 엔드포인트
    @PostMapping("/feedback/analysis/{meetingId}")
    @Operation(summary = "특정 회의 기반 개인 AI 피드백 생성", description = "전체 프로젝트 진행도와 이번 회의 내용을 분석하여 개별 피드백 요약 및 상세본을 생성합니다.")
    public ResponseEntity<Void> createMeetingFeedbackAnalysis(
            @PathVariable final Long meetingId,
            @RequestParam(name = "memberId") final Long memberId,
            @RequestParam(name = "projectId") final Long projectId) {

        commentService.generateMeetingAIAnalysis(memberId, meetingId, projectId);
        return ResponseEntity.ok().build();
    }

    // 1. 피드백 요약 조회 (진짜 데이터 연동 완료)
    @GetMapping("/feedback/summary/{meetingId}")
    @Operation(summary = "피드백 요약 조회", description = "특정 회의의 AI 피드백 요약(FEEDBACK_SUM)을 조회합니다.")
    public ResponseEntity<FeedbackDTO> getFeedbackSummary(
            @PathVariable final Long meetingId,
            @RequestParam(name = "memberId") final Long memberId) {

        return ResponseEntity.ok(commentService.getFeedbackSummary(memberId, meetingId));
    }

    // 2. 상세 AI 피드백 조회 (진짜 데이터 연동 완료)
    @GetMapping("/feedback/detail/{meetingId}")
    @Operation(summary = "상세 피드백 조회", description = "특정 회의의 상세 AI 피드백(FEEDBACK)을 조회합니다.")
    public ResponseEntity<FeedbackDTO> getFeedbackDetail(
            @PathVariable final Long meetingId,
            @RequestParam(name = "memberId") final Long memberId) {

        return ResponseEntity.ok(commentService.getFeedbackDetail(memberId, meetingId));
    }

    // 3. 피드백의 기준이 된 요약된 회의 조회 (Meeting 테이블 연동 완료)
    @GetMapping("/feedback/meeting-summary/{meetingId}")
    @Operation(summary = "피드백용 회의 요약 조회", description = "피드백의 근거가 되는 회의 요약 텍스트(Meeting의 aiSummary)를 조회합니다.")
    public ResponseEntity<FeedbackDTO> getMeetingSummary(
            @PathVariable final Long meetingId) {

        return ResponseEntity.ok(commentService.getMeetingSummaryForFeedback(meetingId));
    }
}
