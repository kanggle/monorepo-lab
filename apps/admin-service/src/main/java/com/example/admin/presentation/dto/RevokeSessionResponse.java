package com.example.admin.presentation.dto;

import java.time.Instant;

public record RevokeSessionResponse(
        String accountId,
        int revokedSessionCount,
        String operatorId,
        Instant revokedAt,
        String auditId
) {}
