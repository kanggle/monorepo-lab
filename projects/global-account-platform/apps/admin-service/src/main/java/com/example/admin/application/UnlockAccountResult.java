package com.example.admin.application;

import java.time.Instant;

public record UnlockAccountResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant unlockedAt,
        String auditId
) {}
