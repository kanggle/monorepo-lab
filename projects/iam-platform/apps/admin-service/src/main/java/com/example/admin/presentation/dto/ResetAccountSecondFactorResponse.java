package com.example.admin.presentation.dto;

import java.time.Instant;

/** TASK-MONO-771 S6 — 200 body of {@code POST /api/admin/accounts/{accountId}/2fa/reset} (admin-api.md). */
public record ResetAccountSecondFactorResponse(
        String accountId,
        String operatorId,
        Instant resetAt,
        String auditId
) {}
