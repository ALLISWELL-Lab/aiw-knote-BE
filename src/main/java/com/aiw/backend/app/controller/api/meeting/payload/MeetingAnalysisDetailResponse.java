package com.aiw.backend.app.controller.api.meeting.payload;

import com.aiw.backend.app.controller.api.meeting.payload.action_item.ActionItemResponse;
import java.util.List;

public record MeetingAnalysisDetailResponse(
    Long meetingId,
    String agenda,
    String summaryText, // AI가 만든 JSON 문자열이 들어갈 자리
    String transcript,
    List<ActionItemResponse> actionItems
) {}
