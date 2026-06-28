package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.application.usecase.NotificationAggregationResult;
import com.kanggle.platformconsole.bff.application.usecase.NotificationAggregationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Console notification aggregator route — the ADR-MONO-043 D2 / P3a surface that
 * feeds the single shared-shell notification bell.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/console/notifications/inbox?page&size&unread} — fans in
 *       the configured per-domain inboxes (Phase-1 = erp) and returns one merged,
 *       {@code createdAt}-desc feed. <b>Always HTTP 200</b> (D5 — failure
 *       isolation HARD INVARIANT: a downed domain yields the other domains' items
 *       plus a {@code degradedDomains} entry; the bell NEVER 5xx's because one
 *       domain is down — the § 1.2 regression this ADR forbids).</li>
 *   <li>{@code POST /api/console/notifications/{sourceDomain}/{id}/read} —
 *       proxies an idempotent mark-read to the owning domain (contract § 4.5);
 *       a {@code sourceDomain} that is not a configured inbox domain → 404
 *       {@code NOTIFICATION_NOT_FOUND}.</li>
 * </ul>
 *
 * <p>Inbound auth: secured like every other console-bff route — Spring Security
 * OAuth2 Resource Server bearer (the inbound principal is the GAP/IAM OIDC token;
 * any path not on {@code PublicPaths} is {@code authenticated()}). The bearer is
 * re-dispatched per-domain via {@code CredentialSelectionPort} (D6) inside the
 * use-case; this controller never touches a credential.
 *
 * <p>Unlike {@code OperatorOverviewController}, NO {@code X-Tenant-Id} is required
 * inbound: the only Phase-1 domain (erp) resolves its tenant from the JWT
 * {@code tenant_id} claim (D6 / contract § 3), so the aggregator does not gate on
 * an active-tenant header.
 *
 * <p>{@code asOf} is the server-side request entry timestamp ({@code Instant.now()}
 * at handler entry — not per-leg response time).
 */
@RestController
public class NotificationAggregatorController {

    private final NotificationAggregationUseCase aggregationUseCase;

    public NotificationAggregatorController(NotificationAggregationUseCase aggregationUseCase) {
        this.aggregationUseCase = aggregationUseCase;
    }

    @GetMapping("/api/console/notifications/inbox")
    public ResponseEntity<NotificationInboxResponse> inbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unread) {
        // asOf = server-side request entry (D2 — not per-leg response time).
        Instant asOf = Instant.now();

        // Fan-in — the use-case fires one leg per configured domain, merges +
        // injects sourceDomain + sorts, and records per-domain degrade (D5). It
        // NEVER throws for a downed domain; the response is always 200.
        NotificationAggregationResult result = aggregationUseCase.aggregate(page, size, unread);

        NotificationInboxResponse body = new NotificationInboxResponse(
                asOf,
                result.items(),
                new NotificationInboxResponse.Meta(page, size, result.totalElements()),
                result.degradedDomains());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/console/notifications/{sourceDomain}/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable String sourceDomain,
            @PathVariable String id) {
        // Dispatch to the owning domain (D6 per-domain credential). An unknown
        // sourceDomain throws UnknownNotificationDomainException → 404
        // NOTIFICATION_NOT_FOUND (GlobalExceptionHandler).
        Map<String, Object> body = aggregationUseCase.markRead(sourceDomain, id);
        return ResponseEntity.ok(body);
    }
}
