package com.example.admin.presentation.dto;

import java.time.Instant;

public record LockAccountResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant lockedAt,
        String auditId
) {}
