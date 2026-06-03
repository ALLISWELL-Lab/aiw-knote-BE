package com.aiw.backend.app.model.invite.service;

import com.aiw.backend.app.model.invite.domain.Invite;
import com.aiw.backend.app.model.invite.dto.InviteDTO;
import com.aiw.backend.app.model.invite.dto.InviteJoinRequest;
import com.aiw.backend.app.model.invite.repository.InviteRepository;
import com.aiw.backend.app.model.member.domain.Member;
import com.aiw.backend.app.model.member.repository.MemberRepository;
import com.aiw.backend.app.model.team.domain.Team;
import com.aiw.backend.app.model.team.repository.TeamRepository;
import com.aiw.backend.app.model.team.service.TeamService;
import com.aiw.backend.app.model.team_member.domain.TeamMember;
import com.aiw.backend.app.model.team_member.repository.TeamMemberRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class InviteService {

  private final InviteRepository inviteRepository;
  private final MemberRepository memberRepository;
  private final TeamService teamService;
  private final TeamRepository teamRepository;
  private final TeamMemberRepository teamMemberRepository;

  @Transactional
  public void joinTeamByToken(String email, InviteJoinRequest request) {
    // 1. 로그인한 유저 확인하여 ID(Long) 확보
    Member member = memberRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

    // 2. 팀원분이 만들어 두신 joinTeam(초대코드, 멤버ID) 메서드를 그대로 호출 🚀
    teamService.joinTeam(request.getInviteToken(), member.getId());
  }

  // 팀 ID로 활성화된 초대 코드 조회
  @Transactional(readOnly = true)
  public InviteDTO getInviteCodeByTeam(Long teamId) {
    Invite invite = inviteRepository.findByTeamIdAndActivatedTrue(teamId)
        .orElseThrow(() -> new IllegalArgumentException("해당 팀에 활성화된 초대 코드가 없습니다."));

    // 엔티티를 DTO로 변환하여 반환 (매핑 구조에 맞게 커스텀)
    InviteDTO dto = new InviteDTO();
    dto.setId(invite.getId());
    dto.setInviteToken(invite.getInviteToken());
    dto.setExpiresAt(invite.getExpiresAt());
    dto.setActivated(invite.getActivated());
    dto.setTeam(invite.getTeam().getId());
    return dto;
  }

  // 기존 코드 폐기 후 10자리 난수로 초대 코드 재발급
  @Transactional
  public InviteDTO regenerateInviteCode(String email, Long teamId) {
    // 1. 기존에 켜져 있던 초대 코드가 있다면 찾아서 비활성화(폐기) 처리
    inviteRepository.findByTeamIdAndActivatedTrue(teamId)
        .ifPresent(existingInvite -> {
          existingInvite.setActivated(false);
          existingInvite.setRevokedAt(java.time.LocalDateTime.now());
          inviteRepository.save(existingInvite);
        });

    // 2. 10자리 문자열 난수 생성 (아까 정의한 규칙 적용)
    String newGeneratedCode = InviteCodeGenerator.generateInviteCode();

    // 3. 새 Invite 엔티티 구워서 DB에 세이브
    Team team = teamRepository.findById(teamId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀입니다."));

    Invite newInvite = new Invite();
    newInvite.setInviteToken(newGeneratedCode);
    newInvite.setTeam(team);
    newInvite.setActivated(true);
    newInvite.setExpiresAt(java.time.LocalDateTime.now().plusYears(1)); // 유효기한 1년 넉넉하게 보장

    inviteRepository.save(newInvite);

    // 4. 리턴용 DTO 바인딩
    InviteDTO dto = new InviteDTO();
    dto.setInviteToken(newGeneratedCode);
    dto.setTeam(teamId);
    return dto;
  }

  public class InviteCodeGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 10;
    private static final SecureRandom random = new SecureRandom();

    public static String generateInviteCode() {
      StringBuilder sb = new StringBuilder(CODE_LENGTH);
      for (int i = 0; i < CODE_LENGTH; i++) {
        int index = random.nextInt(CHARACTERS.length());
        sb.append(CHARACTERS.charAt(index));
      }
      return sb.toString();
    }
  }
}
