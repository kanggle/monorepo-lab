package com.example.membership.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivateSubscriptionRequest(
        @NotBlank(message = "planLevel is required") String planLevel,
        @NotBlank(message = "idempotencyKey is required") String idempotencyKey
) {
}
