package com.example.account.application.result;

import java.time.Instant;

public record DeleteAccountResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant gracePeriodEndsAt
) {
}
