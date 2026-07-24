package com.example.fanplatform.membership.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Subscribe request body. {@code tier} validity (the enum value set) is checked
 * in the controller so an unknown value maps to 422 MEMBERSHIP_TIER_INVALID
 * rather than a generic deserialization 400. {@code planMonths >= 1} and the
 * required {@code Idempotency-Key} header are enforced by {@code @Valid} /
 * controller binding.
 */
public record SubscribeRequest(
        @NotBlank(message = "tier is required")
        String tier,

        @Min(value = 1, message = "planMonths must be >= 1")
        int planMonths,

        @Size(max = 80, message = "paymentId must be <= 80 chars")
        String paymentId) {
}
