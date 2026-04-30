package com.example.account.application.result;

import java.time.Instant;

public record StatusChangeResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant changedAt
) {
}
