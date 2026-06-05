package com.example.admin.application;

import java.time.Instant;

public record RevokeSessionResult(
        String accountId,
        int revokedSessionCount,
        String operatorId,
        Instant revokedAt,
        String auditId
) {}
