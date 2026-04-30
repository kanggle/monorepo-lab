package com.example.account.application.result;

import java.time.Instant;

public record GdprDeleteResult(
        String accountId,
        String status,
        String emailHash,
        Instant maskedAt
) {
}
