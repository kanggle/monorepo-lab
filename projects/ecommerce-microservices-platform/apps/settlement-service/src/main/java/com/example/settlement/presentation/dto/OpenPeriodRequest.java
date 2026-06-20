package com.example.settlement.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * {@code POST /periods} request body (settlement-api.md). The half-open window
 * {@code [from, to)}; an empty / inverted window ({@code from ≥ to}) is rejected by
 * the domain → 422 {@code PERIOD_WINDOW_INVALID}.
 */
public record OpenPeriodRequest(
        @NotNull(message = "from is required") Instant from,
        @NotNull(message = "to is required") Instant to) {
}
