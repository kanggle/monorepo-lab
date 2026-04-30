package com.example.admin.application;

import java.time.Instant;

public record QueryAuditCommand(
        String accountId,
        String actionCode,
        Instant from,
        Instant to,
        String source,
        int page,
        int size,
        String idempotencyKey,
        String reason,
        OperatorContext operator
) {}
