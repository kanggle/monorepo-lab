package com.kanggle.platformconsole.bff.application.usecase;

import java.util.List;
import java.util.Map;

/**
 * Result of a {@link NotificationAggregationUseCase} fan-in (ADR-MONO-043 P3a / D2).
 *
 * <p>Carries the merged, {@code createdAt}-desc-sorted feed plus the
 * per-domain degrade attribution required by the failure-isolation invariant
 * (D5 / contract § 4.4): a domain whose leg failed appears in
 * {@link #degradedDomains()} while the other domains' items still render.
 *
 * @param items           the merged inbox items (contract § 1 shape, each carrying
 *                        {@code sourceDomain}), newest-first
 * @param degradedDomains the domains whose leg failed (empty when all succeeded)
 * @param totalElements   the sum of each successful domain's reported total
 *                        (degraded domains contribute 0 — the partial-feed total)
 */
public record NotificationAggregationResult(
        List<Map<String, Object>> items,
        List<String> degradedDomains,
        long totalElements) {
}
