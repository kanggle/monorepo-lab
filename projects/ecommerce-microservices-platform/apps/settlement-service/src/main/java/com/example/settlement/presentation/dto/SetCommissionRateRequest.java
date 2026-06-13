package com.example.settlement.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /commission-rates/{sellerId}} body. {@code rateBps} range
 * ({@code [0, 10000]}) is enforced in the domain ({@code InvalidCommissionRateException}
 * → 422 {@code COMMISSION_RATE_INVALID}), not by a bean-validation annotation, so the
 * 422 contract code is emitted rather than a generic 400.
 */
public record SetCommissionRateRequest(@NotNull(message = "rateBps is required") Integer rateBps) {
}
