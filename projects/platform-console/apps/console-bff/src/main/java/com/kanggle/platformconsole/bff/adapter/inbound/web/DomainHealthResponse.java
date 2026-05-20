package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Response envelope for {@code GET /api/console/dashboards/domain-health}.
 *
 * <p>Schema is byte-verbatim from
 * {@code console-integration-contract.md} § 2.4.9.2 Response Schema:
 *
 * <pre>
 * {
 *   "asOf": "2026-05-21T01:30:00Z",
 *   "cards": [
 *     { "domain": "gap",     "status": "ok",       "data": { "status": "UP" } },
 *     { "domain": "wms",     "status": "ok",       "data": { "status": "UP" } },
 *     { "domain": "scm",     "status": "degraded", "reason": "DOWNSTREAM_ERROR" },
 *     { "domain": "finance", "status": "ok",       "data": { "status": "OUT_OF_SERVICE" } },
 *     { "domain": "erp",     "status": "ok",       "data": { "status": "UP" } }
 *   ]
 * }
 * </pre>
 *
 * <p>Hard invariants:
 * <ul>
 *   <li>{@code cards} is always exactly 5 entries in fixed order
 *       {@code [gap, wms, scm, finance, erp]} regardless of which legs
 *       succeeded.</li>
 *   <li>{@code cards[i].status} ∈ {@code { "ok", "degraded" }} only —
 *       {@code "forbidden"} is NEVER emitted on this route (no permission
 *       outcome exists for a public actuator leg).</li>
 *   <li>{@code asOf} is the server-side request entry timestamp.</li>
 *   <li>{@code data} is present only on {@code ok} cards (carrying the
 *       producer's Spring Boot health JSON, typically {@code {"status": "UP"}});
 *       {@code reason} only on {@code degraded} cards.</li>
 * </ul>
 */
public record DomainHealthResponse(
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
    }
}
