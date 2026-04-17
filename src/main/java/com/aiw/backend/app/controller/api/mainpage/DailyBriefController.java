package com.aiw.backend.app.controller.api.mainpage;

import com.aiw.backend.app.model.comment.dto.DailyBriefDTO;
import com.aiw.backend.app.model.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/dashboard/dailyBrief", produces = MediaType.APPLICATION_JSON_VALUE)
public class DailyBriefController {
    private final CommentService commentService;

    @GetMapping
    @Operation(
            summary = "데일리 브리핑 조회",
            description = "대시보드 메인에 표시할 오늘 일정 요약, AI 코멘트, 예정된 회의 및 할 일 목록을 통합 조회합니다."
    )
    public ResponseEntity<DailyBriefDTO> getDailyBrief(
            @RequestParam(name = "memberId") final Long memberId) {

        // 서비스에서 요약, AI 코멘트, 회의/투두 리스트가 합쳐진 DTO를 가져옴
        DailyBriefDTO dailyBrief = commentService.getDailyBrief(memberId);
        return ResponseEntity.ok(dailyBrief);
    }
}
