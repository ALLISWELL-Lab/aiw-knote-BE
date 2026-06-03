package com.aiw.backend.app.model.team.service;

import com.aiw.backend.app.model.member.domain.Member;
import com.aiw.backend.app.model.member.dto.MemberDTO;
import com.aiw.backend.app.model.member.repository.MemberRepository;
import com.aiw.backend.app.model.team_member.domain.TeamMember;
import com.aiw.backend.app.model.team_member.repository.TeamMemberRepository;
import com.aiw.backend.events.BeforeDeleteTeam;
import com.aiw.backend.app.model.team.domain.Team;
import com.aiw.backend.app.model.team.dto.TeamDTO;
import com.aiw.backend.app.model.team.repository.TeamRepository;
import com.aiw.backend.util.CustomCollectors;
import com.aiw.backend.util.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import java.util.stream.Collectors;
import lombok.Builder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository; // 추가
    private final TeamMemberRepository teamMemberRepository;
    private final ApplicationEventPublisher publisher;

    public TeamService(final TeamRepository teamRepository,
                       final MemberRepository memberRepository,
                       final TeamMemberRepository teamMemberRepository,
            final ApplicationEventPublisher publisher) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.publisher = publisher;
    }

    public List<TeamDTO> findAll() {
        final List<Team> teams = teamRepository.findAll(Sort.by("id"));
        return teams.stream()
                .map(team -> mapToDTO(team, new TeamDTO()))
                .toList();
    }

    public TeamDTO get(final Long id) {
        return teamRepository.findById(id)
                .map(team -> mapToDTO(team, new TeamDTO()))
                .orElseThrow(NotFoundException::new);
    }

    // 랜덤 코드 중복 가능성 때문에 해당 메서드 사용
  private String generateInviteCode() {
    String inviteCode;
    do {
      inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    } while (teamRepository.existsByInviteCode(inviteCode));
    return inviteCode;
  }

    @Transactional
    public TeamDTO create(final TeamDTO teamDTO) {
      // 1. 롬복 빌더 패턴을 사용하여 DTO를 Team 엔티티로 완벽하게 변환 및 생성합니다.
      Team team = Team.builder()
          .name(teamDTO.getName())
          .inviteCode(teamDTO.getInviteCode())
          .activated(true) // 기본 활성화
          .build();

      // 2. 데이터베이스에 팀을 먼저 저장합니다.
      Team savedTeam = teamRepository.save(team);

      // 3. DTO에 이미 담겨있는 leaderId를 꺼내와서 팀장(LEADER) 매핑을 진행합니다 🚀
      Long leaderId = teamDTO.getLeaderId();
      if (leaderId != null) {
        Member leader = memberRepository.findById(leaderId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        TeamMember teamLeader = new TeamMember();
        teamLeader.setTeam(savedTeam);     // 방금 생성된 진짜 팀 객체 매핑
        teamLeader.setMember(leader);      // 로그인한 나(멤버) 매핑
        teamLeader.setRole("LEADER");     // 역할은 팀장!
        teamLeader.setActivated(true);

        teamMemberRepository.save(teamLeader);
        System.out.println("🎉 [팀장 자동 등록] 팀 ID: " + savedTeam.getId() + "번에 유저 " + leader.getName() + "님이 팀장으로 조인되었습니다.");
      }

      // 4. 팀원분들의 응답 컨벤션에 맞게 빌더 패턴으로 최종 반환할 TeamDTO를 조립합니다.
      return TeamDTO.builder()
          .id(savedTeam.getId())
          .name(savedTeam.getName())
          .inviteCode(savedTeam.getInviteCode())
          .activated(savedTeam.getActivated())
          .leaderId(leaderId)
          .message("팀이 정상적으로 생성되고 팀장이 임명되었습니다.")
          .build();
    }

    //팀 멤버 추가
    public TeamDTO joinTeam(final String inviteCode, final Long memberId){
        // 1. 초대 코드로 팀 찾기 (TeamRepository에 findByInviteCode 추가 필요)
        Team team = teamRepository.findAll().stream()
                .filter(t -> t.getInviteCode().equals(inviteCode) && t.getActivated())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("유효하지 않은 초대 코드입니다."));

        // 2. 이미 가입된 멤버인지 확인
        Optional<TeamMember> existingMember = teamMemberRepository.findByTeamIdAndMemberId(team.getId(), memberId);
        if (existingMember.isPresent() && existingMember.get().getActivated()) {
            throw new IllegalStateException("이미 가입된 팀입니다.");
        }

        // 3. 팀 멤버 등록 (또는 재활성화)
        TeamMember teamMember = existingMember.orElse(new TeamMember());
        teamMember.setTeam(team);
        teamMember.setMember(memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다.")));
        teamMember.setRole("MEMBER"); // 초대 링크로 들어오면 일반 멤버
        teamMember.setActivated(true);

        teamMemberRepository.save(teamMember);

        // 4. 응답 구성
        TeamDTO response = new TeamDTO();
        response.setId(team.getId());
        response.setName(team.getName());
        response.setInviteCode(team.getInviteCode());
        response.setMessage("팀 참여에 성공했습니다.");
        response.setActivated(true);
        return response;
    }

    public boolean update(final Long id, final TeamDTO teamDTO) {
        final Team team = teamRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        // 명세서 상 이름 위주 수정
        if (teamDTO.getName() != null) {
            team.setName(teamDTO.getName());
            teamRepository.save(team);
            return true;
        }
        return false;
    }

    public boolean delete(final Long id) {
        final Team team = teamRepository.findById(id)
                .orElseThrow(NotFoundException::new);

        // 1. 활성화된 팀원 수 체크
        long activeCount = teamMemberRepository.countByTeamIdAndActivatedTrue(id);

        if (activeCount > 1) {
            // 팀원이 더 남아있으면 삭제 실패
            return false;
        }

        // 2. 마지막 1인이면 Soft Delete 진행
        team.setActivated(false);
        teamRepository.save(team);

        publisher.publishEvent(new BeforeDeleteTeam(id));

        return true;
    }

    //팀 탈퇴
    public TeamDTO leaveTeam(final Long teamId, final Long memberId, final Long delegateMemberId) {
        // 1. 팀원 존재 여부 및 활성화 상태 확인
        TeamMember me = teamMemberRepository.findByTeamIdAndMemberId(teamId, memberId)
                .orElseThrow(() -> new NotFoundException("팀 멤버 정보를 찾을 수 없습니다."));

        if (!me.getActivated()) {
            throw new IllegalStateException("이미 탈퇴 처리된 멤버입니다.");
        }

        TeamDTO response = new TeamDTO();
        response.setId(teamId);

        // 2. 팀장 권한 처리
        if ("LEADER".equals(me.getRole())) {
            if (delegateMemberId == null) {
                throw new IllegalStateException("팀장은 권한을 위임할 대상을 지정해야 탈퇴할 수 있습니다.");
            }

            // 위임받을 대상 찾기
            TeamMember successor = teamMemberRepository.findByTeamIdAndMemberId(teamId, delegateMemberId)
                    .orElseThrow(() -> new NotFoundException("권한을 위임받을 멤버를 찾을 수 없습니다."));

            if (!successor.getActivated()) {
                throw new IllegalStateException("탈퇴한 멤버에게는 권한을 위임할 수 없습니다.");
            }

            // 권한 위임 실행
            successor.setRole("LEADER");
            teamMemberRepository.save(successor);
            response.setNewLeaderId(successor.getMember().getId());
        }

        // 3. 본인 Soft Delete 처리 (activated = false)
        me.setActivated(false);
        teamMemberRepository.save(me);

        // 4. 응답 구성
        response.setLeft(true);
        response.setActivated(false);

        return response;
    }

    private TeamDTO mapToDTO(final Team team, final TeamDTO teamDTO) {
        teamDTO.setId(team.getId());
        teamDTO.setName(team.getName());
        teamDTO.setInviteCode(team.getInviteCode());
        teamDTO.setActivated(team.getActivated());

        //팀장 추가 로직
        teamMemberRepository.findByTeamIdAndRoleAndActivatedTrue(team.getId(), "LEADER")
                .ifPresent(leader -> {
                    teamDTO.setLeaderId(leader.getMember().getId());
                    teamDTO.setLeaderName(leader.getMember().getName());
                });

        return teamDTO;
    }

    private Team mapToEntity(final TeamDTO teamDTO, final Team team) {
        team.setName(teamDTO.getName());
        team.setInviteCode(teamDTO.getInviteCode());
        team.setActivated(teamDTO.getActivated());
        return team;
    }

    public Map<Long, Long> getTeamValues() {
        return teamRepository.findAll(Sort.by("id"))
                .stream()
                .collect(CustomCollectors.toSortedMap(Team::getId, Team::getId));
    }

    // 팀원 가져오기
    public List<MemberDTO> getTeamMembers(Long teamId) {
      // 1. 해당 팀 ID로 조회한 팀원 매핑 테이블(TeamMember)에서 유저들을 긁어옵니다.
      return teamMemberRepository.findByTeamId(teamId).stream()
          .map(teamMember -> {
            Member member = teamMember.getMember(); // 유저 엔티티 낚아채기
            return MemberDTO.builder()
                .id(member.getId())
                .name(member.getName())
                .email(member.getEmail())
                .build();
          })
          .collect(Collectors.toList());
    }
}
