package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LockAccountRequest(
        @NotBlank(message = "Reason is required")
        String reason,

        String operatorId,
        String ruleCode,
        Integer riskScore,
        String suspiciousEventId,
        String detectedAt,
        String ticketId
) {
}
