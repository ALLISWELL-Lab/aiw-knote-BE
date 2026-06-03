package com.aiw.backend.app.model.invite.repository;

import com.aiw.backend.app.model.invite.domain.Invite;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface InviteRepository extends JpaRepository<Invite, Long> {

  // 활성화 상태이면서 입력한 토큰과 일치하는 초대 정보 찾기
  Optional<Invite> findByInviteTokenAndActivatedTrue(String inviteToken);

  // 특정 팀에 속한 활성화된 초대 정보 찾기 (조회용)
  Optional<Invite> findByTeamIdAndActivatedTrue(Long teamId);
}
