package com.kanggle.platformconsole.bff.adapter.inbound.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response envelope for {@code GET /api/console/notifications/inbox}
 * (ADR-MONO-043 P3a / D2).
 *
 * <pre>
 * {
 *   "asOf": "2026-06-28T01:23:45Z",
 *   "items": [ { ...§1-shaped item..., "sourceDomain": "erp" }, ... ],
 *   "meta": { "page": 0, "size": 20, "totalElements": 3 },
 *   "degradedDomains": [ "erp" ]
 * }
 * </pre>
 *
 * <p>Hard invariants:
 * <ul>
 *   <li>Always HTTP 200 (D5 — failure isolation; one domain down ≠ whole bell down).
 *       A down domain yields {@code items} from the others +
 *       {@code degradedDomains} naming the failed domain.</li>
 *   <li>{@code items} are {@code platform/contracts/notification-inbox-contract.md}
 *       § 1 shaped, each carrying {@code sourceDomain} (injected by the aggregator
 *       when a domain omits it — contract § 4.2), sorted by {@code createdAt} desc.</li>
 *   <li>{@code asOf} is the server-side request entry timestamp (not per-leg).</li>
 * </ul>
 *
 * @param asOf            server-side request entry timestamp
 * @param items           merged, newest-first inbox items (each with {@code sourceDomain})
 * @param meta            paging metadata ({@code page}, {@code size}, {@code totalElements})
 * @param degradedDomains domains whose leg failed (empty when all succeeded)
 */
public record NotificationInboxResponse(
        Instant asOf,
        List<Map<String, Object>> items,
        Meta meta,
        List<String> degradedDomains) {

    public record Meta(int page, int size, long totalElements) {
    }
}
