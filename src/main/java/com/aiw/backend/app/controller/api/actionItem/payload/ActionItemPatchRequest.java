package com.aiw.backend.app.controller.api.actionItem.payload;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionItemPatchRequest {
  private String title;         // 수정할 제목 (없으면 null)
  private LocalDateTime dueDate; // 수정할 기한 (없으면 null)
  private Long assigneeId;
}
