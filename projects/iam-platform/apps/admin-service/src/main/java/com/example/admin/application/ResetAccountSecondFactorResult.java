package com.example.admin.application;

import java.time.Instant;

/** TASK-MONO-771 S6 — the public 200 body of {@code POST /api/admin/accounts/{accountId}/2fa/reset}. */
public record ResetAccountSecondFactorResult(
        String accountId,
        String operatorId,
        Instant resetAt,
        String auditId
) {}
