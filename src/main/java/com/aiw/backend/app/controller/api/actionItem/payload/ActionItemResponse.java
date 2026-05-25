package com.aiw.backend.app.controller.api.actionItem.payload;

public record ActionItemResponse(
    Long id,
    String title,
    String memo,
    Boolean isCompleted
) {}

