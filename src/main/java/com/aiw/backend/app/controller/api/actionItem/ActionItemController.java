package com.aiw.backend.app.controller.api.actionItem;

import com.aiw.backend.app.controller.api.actionItem.payload.ActionItemPatchRequest;
import com.aiw.backend.app.model.action_item.dto.ActionItemDTO;
import com.aiw.backend.app.model.action_item.service.ActionItemService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/action-items", produces = MediaType.APPLICATION_JSON_VALUE) // 경로를 v1 표준에 맞춤
@RequiredArgsConstructor
public class ActionItemController {

  private final ActionItemService actionItemService;

  // 1. 액션아이템 전체 조회 (담당자별 혹은 전체)
  @GetMapping
  @Operation(summary = "액션아이템 전체 조회", description = "memberId가 있으면 해당 담당자의 것만, 없으면 전체를 조회합니다.")
  public ResponseEntity<List<ActionItemDTO>> getAllActionItems(
      @RequestParam(name = "memberId", required = false) final Long memberId) {
    // Service에 getActionItems(memberId) 메서드가 살아있는지 확인 필요
    return ResponseEntity.ok(actionItemService.getActionItems(memberId));
  }

  // 2. 특정 회의의 액션아이템 조회 (UI 투두-담당자 매칭용)
  @GetMapping("/meeting/{meetingId}")
  @Operation(summary = "회의별 액션아이템 조회")
  public ResponseEntity<List<ActionItemDTO>> getByMeeting(@PathVariable Long meetingId) {
    return ResponseEntity.ok(actionItemService.getByMeetingId(meetingId));
  }

  // 3. 액션아이템 수정 (스프린트 페이지: 제목/기한/담당자 실시간 변경)
  // 기존 PUT 대신 PATCH를 사용하여 사용자가 수정한 필드만 반영합니다.
  @PatchMapping("/{id}")
  @Operation(summary = "액션아이템 부분 수정", description = "제목, 마감기한, 담당자 중 전송된 필드만 수정합니다.")
  public ResponseEntity<Void> patchActionItem(
      @PathVariable Long id,
      @RequestBody ActionItemPatchRequest request) {
    actionItemService.patchActionItem(id, request);
    return ResponseEntity.ok().build();
  }

  // 4. 액션아이템 삭제
  @DeleteMapping("/{id}")
  @Operation(summary = "액션아이템 삭제")
  public ResponseEntity<Void> deleteActionItem(@PathVariable final Long id) {
    // Service에 delete 메서드를 구현해야 에러가 안 납니다.
    actionItemService.delete(id);
    return ResponseEntity.noContent().build();
  }
}