package com.example.payment.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Payment creation request body.
 *
 * <p>{@code userId} is intentionally NOT a field here. The authenticated caller
 * identity must be sourced from the {@code X-User-Id} header injected by the
 * gateway. See specs/contracts/http/payment-api.md and TASK-BE-128.
 */
public record PaymentCreateRequest(
        @NotBlank String orderId,
        @Positive long amount
) {
}
