package com.aiw.backend.app.model.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InviteJoinRequest {

  @NotBlank(message = "초대코드는 필수 입력 값입니다.")
  @Size(min = 8, max = 8, message = "초대코드는 정확히 8자리여야 합니다.")
  private String inviteToken;
}
