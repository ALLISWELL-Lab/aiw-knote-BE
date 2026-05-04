package com.aiw.backend.app.controller.api.meeting.payload.action_item;

public record ActionItemResponse(
    Long id,
    String title,
    String memo,
    Boolean isCompleted
) {}

