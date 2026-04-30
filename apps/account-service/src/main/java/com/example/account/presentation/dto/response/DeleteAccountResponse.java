package com.example.account.presentation.dto.response;

import com.example.account.application.result.DeleteAccountResult;

import java.time.Instant;

public record DeleteAccountResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant gracePeriodEndsAt
) {
    public static DeleteAccountResponse from(DeleteAccountResult result) {
        return new DeleteAccountResponse(
                result.accountId(),
                result.previousStatus(),
                result.currentStatus(),
                result.gracePeriodEndsAt()
        );
    }
}
