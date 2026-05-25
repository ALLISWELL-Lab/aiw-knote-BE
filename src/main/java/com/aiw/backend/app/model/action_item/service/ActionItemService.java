package com.aiw.backend.app.model.action_item.service;

import com.aiw.backend.app.controller.api.actionItem.payload.ActionItemPatchRequest;
import com.aiw.backend.app.model.action_item.domain.ActionItem;
import com.aiw.backend.app.model.action_item.dto.ActionItemDTO;
import com.aiw.backend.app.model.action_item.repository.ActionItemRepository;
import com.aiw.backend.app.model.member.domain.Member;
import com.aiw.backend.app.model.member.repository.MemberRepository;
import com.aiw.backend.util.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class ActionItemService {

  private final ActionItemRepository actionItemRepository;
  private final MemberRepository memberRepository;

  public ActionItemService(final ActionItemRepository actionItemRepository,
      final MemberRepository memberRepository) {
    this.actionItemRepository = actionItemRepository;
    this.memberRepository = memberRepository;
  }

  // 액션아이템 조회 (전체 / 특정 담당자별)
  @Transactional(readOnly = true)
  public List<ActionItemDTO> getActionItems(final Long assigneeMemberId) {
    List<ActionItem> actionItems;

    if (assigneeMemberId != null) {
      // 특정 멤버에게 할당된 할 일만 조회
      actionItems = actionItemRepository.findByAssigneeMemberId(assigneeMemberId);
    } else {
      // 전체 할 일 조회
      actionItems = actionItemRepository.findAll(Sort.by("id"));
    }

    return actionItems.stream()
        .map(item -> mapToDTO(item, new ActionItemDTO()))
        .collect(Collectors.toList());
  }

 // 스프린트 페이지/화면에서 제목이나 마감 기한을 실시간으로 수정할 때 사용
  public void patchActionItem(Long id, ActionItemPatchRequest request) {
    ActionItem actionItem = actionItemRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("해당 ActionItem이 없습니다. id=" + id));

    // 제목 수정 (커서로 이름 바꿀 때)
    if (request.getTitle() != null && !request.getTitle().isBlank()) {
      actionItem.setTitle(request.getTitle());
    }

    // 기한 수정 (달력으로 바꿀 때)
    if (request.getDueDate() != null) {
      actionItem.setDueDate(request.getDueDate());
    }

    // 담당자 변경
    if (request.getAssigneeId() != null) {
      Member assignee = memberRepository.findById(request.getAssigneeId())
          .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
      actionItem.setAssigneeMember(assignee);
    }

    // @Transactional에 의해 자동 업데이트(Dirty Checking)
  }

  // 회의별 액션 아이템 조회 (UI의 투두-담당자 매칭 화면용)
  @Transactional(readOnly = true)
  public List<ActionItemDTO> getByMeetingId(Long meetingId) {
    // 명시적으로 List<ActionItem> 타입을 선언하여 타입 추론 에러를 방지합니다.
    List<ActionItem> actionItems = actionItemRepository.findByMeetingId(meetingId);

    return actionItems.stream()
        .map(item -> mapToDTO(item, new ActionItemDTO()))
        .collect(Collectors.toList());
  }

  // 액션아이템 삭제
  @Transactional
  public void delete(final Long id) {
    final ActionItem actionItem = actionItemRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("해당 할 일을 찾을 수 없습니다. id=" + id));

    // 연관 관계나 제약 조건이 있다면 여기서 처리 후 삭제
    actionItemRepository.delete(actionItem);
  }

  // --- 기존 DTO 매핑 헬퍼 메서드 ---
  private ActionItemDTO mapToDTO(final ActionItem actionItem, final ActionItemDTO actionItemDTO) {
    actionItemDTO.setId(actionItem.getId());
    actionItemDTO.setTitle(actionItem.getTitle());
    actionItemDTO.setDueDate(actionItem.getDueDate());
    actionItemDTO.setCompleted(actionItem.getCompleted());
    actionItemDTO.setMemo(actionItem.getMemo());
    // null 체크를 포함하여 안전하게 매핑
    actionItemDTO.setMeeting(actionItem.getMeeting() != null ? actionItem.getMeeting().getId() : null);
    actionItemDTO.setAssigneeMember(actionItem.getAssigneeMember() != null ? actionItem.getAssigneeMember().getId() : null);
    return actionItemDTO;
  }
}
