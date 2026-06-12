package com.example.finance.ledger.presentation.dto;

import java.time.Instant;

/**
 * POST /periods request (ledger-api.md § 5) — the half-open {@code [from, to)}
 * window as ISO-8601 instants.
 */
public record OpenPeriodRequest(Instant from, Instant to) {
}
