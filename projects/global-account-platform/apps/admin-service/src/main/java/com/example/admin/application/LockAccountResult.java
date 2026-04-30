package com.example.admin.application;

import java.time.Instant;

public record LockAccountResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant lockedAt,
        String auditId
) {}
