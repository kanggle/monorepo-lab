package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.GetFxRatesUseCase;
import com.example.finance.ledger.application.view.FxRatesView;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.FxRatesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FX rate feed read endpoint (25th increment — TASK-FIN-BE-033, ADR-002 read surface).
 *
 * <p>Exposes the live contents of the {@code fx_rate_quote} cache so operators can
 * verify what rate the settlement / revaluation feed fallback would apply and how
 * fresh each pair's quote is. Each quote carries:
 * <ul>
 *   <li>exact decimal {@code rate} (string — F5 wire convention);</li>
 *   <li>{@code asOf} / {@code fetchedAt} ISO-8601 timestamps;</li>
 *   <li>{@code ageSeconds} ({@code now − asOf});</li>
 *   <li>{@code stale} flag (mirrors the
 *       {@link com.example.finance.ledger.application.ResolveEffectiveFxRate} staleness
 *       boundary: {@code now − asOf > staleAfter} → stale; {@code ==} is fresh).</li>
 * </ul>
 * The top-level {@code feedEnabled} flag mirrors
 * {@link com.example.finance.ledger.application.port.outbound.FxRateFeedSettings#feedEnabled()}.
 *
 * <p><b>Security:</b> {@link ActorContextResolver#currentOrThrow()} enforces authentication
 * exactly like every other ledger endpoint. The path {@code /api/finance/ledger/fx-rates} falls
 * under the existing {@code /api/finance/**} {@code .authenticated()} rule in
 * {@link com.example.finance.ledger.infrastructure.security.SecurityConfig} — no extra security
 * configuration is needed.
 *
 * <p><b>Tenant-agnostic:</b> {@code fx_rate_quote} has no {@code tenant_id} column; market rates
 * are global. The authenticated operator receives the full cache regardless of tenant (AC-5).
 *
 * <p><b>Net-zero / read-only:</b> no write path, no idempotency key, no migration.
 */
@RestController
@RequestMapping("/api/finance/ledger/fx-rates")
@RequiredArgsConstructor
public class FxRateController {

    private final GetFxRatesUseCase getFxRates;

    /**
     * Return all cached FX rate quotes with staleness metadata.
     *
     * <p>An empty cache (feed never polled / disabled) returns
     * {@code { feedEnabled: <bool>, rates: [] }} — 200, not 404 (AC-1).
     *
     * @return 200 {@code ApiEnvelope<FxRatesResponse>}; 401/403 when unauthenticated
     */
    @GetMapping
    public ResponseEntity<ApiEnvelope<FxRatesResponse>> list() {
        ActorContextResolver.currentOrThrow(); // enforce authentication (AC-5)
        FxRatesView view = getFxRates.get();
        return ResponseEntity.ok(ApiEnvelope.of(FxRatesResponse.from(view)));
    }
}
