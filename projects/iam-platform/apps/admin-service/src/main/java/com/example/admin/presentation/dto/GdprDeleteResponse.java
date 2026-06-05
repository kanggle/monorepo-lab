package com.example.admin.presentation.dto;

import java.time.Instant;

public record GdprDeleteResponse(
        String accountId,
        String status,
        Instant maskedAt,
        String auditId
) {}
