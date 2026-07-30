package com.example.security.service.query.dto;

import java.time.Instant;

public record SuspiciousEventView(
        String id,
        String accountId,
        String ruleCode,
        int riskScore,
        String actionTaken,
        Object evidence,
        Instant detectedAt
) {
}
