package com.example.fanplatform.membership.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Billing-key enrollment request body. {@code tier} validity (the enum value set)
 * is checked in the controller so an unknown value maps to 422
 * {@code MEMBERSHIP_TIER_INVALID} (consistent with subscribe) rather than a generic
 * 400. {@code billingKey} is the opaque value the frontend obtained from
 * {@code PortOne.requestIssueBillingKey(...)}.
 */
public record EnrollBillingKeyRequest(
        @NotBlank(message = "tier is required")
        String tier,

        @NotBlank(message = "billingKey is required")
        @Size(max = 512, message = "billingKey must be <= 512 chars")
        String billingKey) {
}
