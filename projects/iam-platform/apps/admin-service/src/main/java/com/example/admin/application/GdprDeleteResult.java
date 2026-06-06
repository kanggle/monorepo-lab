package com.example.admin.application;

import java.time.Instant;

public record GdprDeleteResult(
        String accountId,
        String status,
        Instant maskedAt,
        String auditId
) {}
