package com.aiw.backend.app.controller.api.meeting.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateMeetingRecordRequest {

  private Long projectId;
  private String agenda;
  private String description;

  // startedAt 은 서버에서 넣어주는 거로 따로 프론트 보내지 X
}
