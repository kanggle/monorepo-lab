package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Response envelope for {@code GET /api/console/dashboards/operator-overview}.
 *
 * <p>Schema is byte-verbatim from
 * {@code console-integration-contract.md} § 2.4.9.1 Response Schema:
 *
 * <pre>
 * {
 *   "asOf": "2026-05-20T10:30:00Z",
 *   "cards": [
 *     { "domain": "iam",     "status": "ok",         "data": { ... } },
 *     { "domain": "wms",     "status": "ok",         "data": { ... } },
 *     { "domain": "scm",     "status": "degraded",   "reason": "DOWNSTREAM_ERROR" },
 *     { "domain": "finance", "status": "forbidden",  "reason": "TENANT_FORBIDDEN" },
 *     { "domain": "erp",     "status": "ok",         "data": { ... } }
 *   ]
 * }
 * </pre>
 *
 * <p>Hard invariants:
 * <ul>
 *   <li>{@code cards} is always exactly 5 entries in fixed order
 *       {@code [gap, wms, scm, finance, erp]} regardless of which legs
 *       succeeded.</li>
 *   <li>{@code asOf} is the server-side request entry timestamp (not per-leg).</li>
 *   <li>{@code data} is present only on {@code ok} cards; {@code reason} only on
 *       {@code degraded}/{@code forbidden} cards (Jackson
 *       {@code @JsonInclude(NON_NULL)} elides the unused field).</li>
 * </ul>
 */
public record OperatorOverviewResponse(
        Instant asOf,
        List<Card> cards
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Card(
            String domain,
            String status,
            Object data,
            String reason
    ) {
        public static Card ok(String domain, Object data) {
            return new Card(domain, "ok", data, null);
        }

        public static Card degraded(String domain, String reason) {
            return new Card(domain, "degraded", null, reason);
        }

        public static Card forbidden(String domain, String reason) {
            return new Card(domain, "forbidden", null, reason);
        }
    }
}
