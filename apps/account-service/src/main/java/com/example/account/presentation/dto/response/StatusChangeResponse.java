package com.example.account.presentation.dto.response;

import com.example.account.application.result.StatusChangeResult;

import java.time.Instant;

public record StatusChangeResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant changedAt
) {
    public static StatusChangeResponse from(StatusChangeResult result) {
        return new StatusChangeResponse(
                result.accountId(),
                result.previousStatus(),
                result.currentStatus(),
                result.changedAt()
        );
    }
}
