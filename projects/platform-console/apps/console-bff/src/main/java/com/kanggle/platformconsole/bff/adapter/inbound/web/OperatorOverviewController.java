package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.adapter.outbound.http.MissingTenantException;
import com.kanggle.platformconsole.bff.adapter.outbound.http.OperatorCredentialContext;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.usecase.OperatorOverviewCompositionUseCase;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MVP "Operator Overview" composition route — the first concrete
 * {@code § 2.4.9.X} BFF surface.
 *
 * <p>Endpoint: {@code GET /api/console/dashboards/operator-overview}.
 * Fans out across all 6 backend domains (GAP + wms + scm + finance + erp +
 * ecommerce) and renders the composed envelope per
 * {@code console-integration-contract.md} § 2.4.9.1.
 *
 * <p>Inbound validation (executes BEFORE any outbound leg fires):
 * <ul>
 *   <li>{@code Authorization} bearer — Spring Security handles
 *       ({@code 401 TOKEN_INVALID} on absent/invalid).</li>
 *   <li>{@code X-Tenant-Id} — required; absent → {@link MissingTenantException}
 *       → {@code 400 NO_ACTIVE_TENANT}.</li>
 *   <li>{@code X-Operator-Token} — required for the GAP leg; absent token on
 *       a GAP-targeted dispatch fails closed inside the credential selector
 *       (per-card {@code forbidden / MISSING_PREREQUISITE}).</li>
 *   <li>{@code X-Finance-Default-Account-Id} — <b>optional</b>; threaded
 *       through to {@link OperatorOverviewCompositionUseCase#compose(String, String)}
 *       per § 2.4.9.1 Option (a) activation (TASK-PC-FE-014). When present
 *       and non-blank, the finance leg fires the balance read for the supplied
 *       account; when absent/blank, the finance leg short-circuits to
 *       {@code forbidden / MISSING_PREREQUISITE} (honest UX for operators
 *       whose {@code admin_operators.finance_default_account_id} is NULL).
 *       Both paths are first-class.</li>
 * </ul>
 *
 * <p>Output: HTTP 200 with the composed envelope. <b>All-down still emits
 * 200</b> with all 6 cards in {@code degraded}/{@code forbidden} states — the
 * route NEVER emits 503 (ADR-MONO-017 D5.B rejection, byte-unchanged).
 * Cross-leg 401 collapses to composition-level 401 (per § 2.4.4 D3 /
 * § 2.4.9.1 — tokens are shared across legs from the same inbound request).
 *
 * <p>{@code asOf} is the server-side request entry timestamp ({@code Instant.now()}
 * at the start of {@link #operatorOverview()} — not per-leg response time).
 */
@RestController
public class OperatorOverviewController {

    private final OperatorOverviewCompositionUseCase compositionUseCase;
    private final OperatorCredentialContext credentialContext;

    public OperatorOverviewController(OperatorOverviewCompositionUseCase compositionUseCase,
                                      OperatorCredentialContext credentialContext) {
        this.compositionUseCase = compositionUseCase;
        this.credentialContext = credentialContext;
    }

    @GetMapping("/api/console/dashboards/operator-overview")
    public ResponseEntity<OperatorOverviewResponse> operatorOverview(
            @RequestHeader(value = "X-Finance-Default-Account-Id", required = false)
            String financeDefaultAccountId) {
        // (1) Fail-closed: X-Tenant-Id required before any outbound (D6.A).
        if (!credentialContext.hasTenant()) {
            throw new MissingTenantException();
        }

        // (2) asOf = server-side composition request entry (§ 2.4.9.1
        //     Implementation guidance: NOT per-leg response time).
        Instant asOf = Instant.now();

        // (3) Compose — use case fires 6 parallel legs, maps outcomes, emits
        //     per-leg + degrade metrics, and returns the fixed-order 6 legs.
        //     The optional X-Finance-Default-Account-Id header is forwarded
        //     verbatim to enable § 2.4.9.1 Option (a) activation on the
        //     finance leg (TASK-PC-FE-014); a null/blank value preserves the
        //     existing MISSING_PREREQUISITE short-circuit (AC-2 regression
        //     guard). The header value is operator profile data flowing on
        //     the request — never credential — so it is NOT logged at INFO
        //     (AC-8 / finance F7 / regulated.md R7 transitive discipline).
        //     Cross-leg 401 escapes as UpstreamUnauthorizedException →
        //     handled by GlobalExceptionHandler (401 TOKEN_INVALID).
        List<CompositionLeg> legs = compositionUseCase.compose(
                credentialContext.getTenantId(), financeDefaultAccountId);

        // (4) Map to wire envelope (§ 2.4.9.1 response schema).
        List<OperatorOverviewResponse.Card> cards = new ArrayList<>(legs.size());
        for (CompositionLeg leg : legs) {
            cards.add(toCard(leg));
        }
        return ResponseEntity.ok(new OperatorOverviewResponse(asOf, cards));
    }

    private static OperatorOverviewResponse.Card toCard(CompositionLeg leg) {
        LegOutcome outcome = leg.outcome();
        String dom = outcome.domain().name().toLowerCase();
        return switch (outcome.status()) {
            case OK -> OperatorOverviewResponse.Card.ok(dom, leg.data());
            case DEGRADED -> OperatorOverviewResponse.Card.degraded(dom, outcome.reason());
            case FORBIDDEN -> OperatorOverviewResponse.Card.forbidden(dom, outcome.reason());
        };
    }
}
