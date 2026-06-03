package com.aiw.backend.app.controller.api.invite;

import com.aiw.backend.app.model.invite.dto.InviteDTO;
import com.aiw.backend.app.model.invite.dto.InviteJoinRequest;
import com.aiw.backend.app.model.invite.service.InviteService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class InviteController {

  private final InviteService inviteService;

  // 초대 코드 검증 및 팀 합류
  @PostMapping("/join")
  public ResponseEntity<?> joinTeam(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody InviteJoinRequest request
  ) {
    String email = userDetails.getUsername();
    inviteService.joinTeamByToken(email, request);
    return ResponseEntity.ok().body(Map.of("message", "팀에 성공적으로 합류했습니다."));
  }


  // 현재 우리 팀의 활성화된 초대 코드 조회하기 (팀 대시보드/설정 페이지용)
  @GetMapping("/teams/{teamId}")
  public ResponseEntity<?> getTeamInviteCode(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable("teamId") Long teamId
  ) {
    // 보안을 위해 서비스단에서 '요청한 유저가 이 팀의 멤버가 맞는지' 검증하는 로직을 거치면 좋습니다.
    InviteDTO inviteDTO = inviteService.getInviteCodeByTeam(teamId);
    return ResponseEntity.ok().body(inviteDTO);
  }


  // 초대 코드 새로고침 / 재발급하기 (팀 설정 페이지용)
  @PostMapping("/teams/{teamId}/regenerate")
  public ResponseEntity<?> regenerateTeamInviteCode(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable("teamId") Long teamId
  ) {
    String email = userDetails.getUsername();

    // 서비스단에서 '요청한 유저가 이 팀의 팀장(Leader)이 맞는지' 검증하고 발급합니다.
    InviteDTO newInviteDTO = inviteService.regenerateInviteCode(email, teamId);

    return ResponseEntity.ok().body(Map.of(
        "message", "초대 코드가 새로 발급되었습니다.",
        "inviteToken", newInviteDTO.getInviteToken()
    ));
  }

}
