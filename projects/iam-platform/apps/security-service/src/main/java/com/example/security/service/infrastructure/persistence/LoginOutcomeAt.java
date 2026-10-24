package com.example.security.service.infrastructure.persistence;

import java.time.Instant;

/**
 * TASK-MONO-811 — the two {@code login_history} columns the performance-metrics aggregate reads
 * ({@code security-query-api.md} § GET /internal/security/metrics/daily). A JPQL constructor
 * projection, so the query never selects a PII column (account id, IP, user agent, fingerprint,
 * country) — not even into memory.
 *
 * @param outcome    {@code login_history.outcome} ({@code SUCCESS} / {@code FAILURE} only — the
 *                   query filters the rest out)
 * @param occurredAt {@code login_history.occurred_at}, the instant the row is attributed to
 */
public record LoginOutcomeAt(String outcome, Instant occurredAt) {
}
