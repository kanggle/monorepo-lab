package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.adapter.outbound.http.MissingTenantException;
import com.kanggle.platformconsole.bff.adapter.outbound.http.OperatorCredentialContext;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.usecase.DomainHealthCompositionUseCase;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 7 "Domain Health Overview" composition route — the second concrete
 * {@code § 2.4.9.X} BFF surface.
 *
 * <p>Endpoint: {@code GET /api/console/dashboards/domain-health}.
 * Fans out across all 5 backend domains' public Spring Boot {@code /actuator/health}
 * endpoints and renders the composed envelope per
 * {@code console-integration-contract.md} § 2.4.9.2.
 *
 * <p>Inbound validation (executes BEFORE any outbound leg fires):
 * <ul>
 *   <li>{@code Authorization} bearer — Spring Security handles
 *       ({@code 401 TOKEN_INVALID} on absent/invalid).</li>
 *   <li>{@code X-Tenant-Id} — required for log MDC + audit traceability;
 *       absent → {@link MissingTenantException} → {@code 400 NO_ACTIVE_TENANT}
 *       (the outbound actuator legs themselves do NOT consume tenant).</li>
 *   <li>{@code X-Operator-Token} — <b>NOT required</b> for this route (no
 *       outbound leg consumes it; the D4 sealed-switch is never invoked).</li>
 * </ul>
 *
 * <p>Output: HTTP 200 with the composed envelope. <b>All-down still emits 200</b>
 * with all 5 cards in {@code degraded} states — the route NEVER emits 503
 * (D5.A discipline, byte-unchanged from § 2.4.9.1).
 *
 * <p>Unlike § 2.4.9.1 (Operator Overview), this route does NOT collapse
 * cross-leg 401 to a composition-level 401 — actuator legs have no shared
 * inbound credential, so a 401 from any leg is itself an unexpected
 * (producer-side actuator misconfiguration) and maps to per-card
 * {@code degraded / DOWNSTREAM_ERROR}.
 */
@RestController
public class DomainHealthController {

    private final DomainHealthCompositionUseCase compositionUseCase;
    private final OperatorCredentialContext credentialContext;

    public DomainHealthController(DomainHealthCompositionUseCase compositionUseCase,
                                  OperatorCredentialContext credentialContext) {
        this.compositionUseCase = compositionUseCase;
        this.credentialContext = credentialContext;
    }

    @GetMapping("/api/console/dashboards/domain-health")
    public ResponseEntity<DomainHealthResponse> domainHealth() {
        // (1) Fail-closed: X-Tenant-Id required for log MDC + audit
        //     traceability (not because outbound legs need it).
        if (!credentialContext.hasTenant()) {
            throw new MissingTenantException();
        }

        // (2) asOf = server-side composition request entry.
        Instant asOf = Instant.now();

        // (3) Compose — use case fires 5 parallel legs (NO credential
        //     pre-resolve, NO sealed-switch invocation).
        List<CompositionLeg> legs = compositionUseCase.compose();

        // (4) Map to wire envelope (§ 2.4.9.2 response schema).
        //     status ∈ {ok, degraded} only — no FORBIDDEN branch.
        List<DomainHealthResponse.Card> cards = new ArrayList<>(legs.size());
        for (CompositionLeg leg : legs) {
            cards.add(toCard(leg));
        }
        return ResponseEntity.ok(new DomainHealthResponse(asOf, cards));
    }

    private static DomainHealthResponse.Card toCard(CompositionLeg leg) {
        LegOutcome outcome = leg.outcome();
        String dom = outcome.domain().name().toLowerCase();
        return switch (outcome.status()) {
            case OK -> DomainHealthResponse.Card.ok(dom, leg.data());
            case DEGRADED, FORBIDDEN ->
                    // FORBIDDEN is unreachable in DomainHealthCompositionUseCase
                    // by construction (no permission decision on actuator legs).
                    // Defensive mapping: if ever hit, classify as degraded with
                    // the original reason for diagnostic visibility.
                    DomainHealthResponse.Card.degraded(dom, outcome.reason());
        };
    }
}
