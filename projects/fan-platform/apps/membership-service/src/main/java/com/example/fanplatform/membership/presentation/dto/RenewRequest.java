package com.example.fanplatform.membership.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Renew request body. The {@code tier} is inherited from the prior membership, so
 * it is not part of the request. {@code planMonths >= 1} and the required
 * {@code Idempotency-Key} header are enforced by {@code @Valid} / controller binding.
 */
public record RenewRequest(
        @Min(value = 1, message = "planMonths must be >= 1")
        int planMonths,

        @Size(max = 80, message = "paymentToken must be <= 80 chars")
        String paymentToken) {
}
