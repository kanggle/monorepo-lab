package com.example.admin.presentation.dto;

import java.time.Instant;

public record UnlockAccountResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant unlockedAt,
        String auditId
) {}
