package com.aiw.backend.app.model.comment.repository;

import com.aiw.backend.app.model.comment.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Comment findFirstByMemberId(Long id);
    Optional<Comment> findByMemberIdAndRefTypeAndRefId(Long memberId, String refType, Long refId);

    // 추가: memberId 조건 없이 회의 ID(refId)와 타입으로만 조회하는 메서드
    Optional<Comment> findByRefTypeAndRefId(String refType, Long refId);
    Optional<Comment> findFirstByMemberIdAndRefTypeOrderByIdDesc(Long memberId, String refType);

    // 중복 데이터가 쌓이더라도 가장 최근에 생성된(ID가 가장 큰) 데이터 1건만 안전하게 가져옵니다.
    Optional<Comment> findFirstByRefTypeAndRefIdOrderByIdDesc(String refType, Long refId);

    // 최근 5개의 피드백 내역을 가져오기 위한 메서드
    List<Comment> findTop5ByMemberIdAndRefTypeOrderByDateCreatedDesc(Long memberId, String refType);
}
