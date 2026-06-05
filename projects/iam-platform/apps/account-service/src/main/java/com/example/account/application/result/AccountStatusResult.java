package com.example.account.application.result;

import java.time.Instant;

public record AccountStatusResult(
        String accountId,
        String status,
        Instant statusChangedAt,
        String reason
) {
}
