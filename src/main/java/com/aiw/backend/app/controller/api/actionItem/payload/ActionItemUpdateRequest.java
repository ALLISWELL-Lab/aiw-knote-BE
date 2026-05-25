package com.aiw.backend.app.controller.api.actionItem.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ActionItemUpdateRequest {

  @Schema(description = "액션아이템 ID", example = "101")
  private Long id;

  @Schema(description = "사용자의 할 일 채택 여부", example = "true")
  private Boolean isSelected; // 체크박스 선택 여부

  @Schema(description = "배정된 담당자 사용자 ID", example = "5")
  private Long assigneeId;

}
