package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.GetFxRateHistoryUseCase;
import com.example.finance.ledger.application.GetFxRatesUseCase;
import com.example.finance.ledger.application.RefreshFxRateQuotesUseCase;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.application.view.FxRateHistorySummaryView;
import com.example.finance.ledger.application.view.FxRatesView;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.FxRateHistoryResponse;
import com.example.finance.ledger.presentation.dto.FxRatesRefreshResponse;
import com.example.finance.ledger.presentation.dto.FxRatesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FX rate feed read + manual refresh endpoints (25th / 27th increments — TASK-FIN-BE-033 /
 * TASK-FIN-BE-040, ADR-002 read surface; 28th increment — TASK-MONO-300, manual refresh).
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
 * <p><b>(27th increment — TASK-FIN-BE-040)</b> also exposes the per-pair FX rate history
 * audit trail via {@code GET /{foreignCurrency}/history} — see {@link #history}.
 *
 * <p><b>(TASK-MONO-300 — manual refresh)</b> adds {@code POST /refresh} so operators can
 * trigger an on-demand cache reload without waiting for the next scheduled tick. The endpoint
 * delegates to {@link RefreshFxRateQuotesUseCase#refresh()} — the same use case the
 * {@code FxRateFeedPoller} calls — and is safe to invoke concurrently with the poller (the
 * upsert is last-write-wins idempotent). No ShedLock on the manual path (deliberate on-demand
 * action; concurrent refreshes are safe). When the feed is disabled
 * ({@code financeplatform.ledger.fxrate.enabled=false}), the use case processes zero pairs and
 * returns 0 — the endpoint returns 200 {@code {feedEnabled:false, refreshed:0}} (a no-op,
 * consistent with the GET returning {@code feedEnabled:false, rates:[]}), NOT an error.
 *
 * <p><b>Security:</b> {@link ActorContextResolver#currentOrThrow()} enforces authentication
 * exactly like every other ledger endpoint. The path {@code /api/finance/ledger/fx-rates} falls
 * under the existing {@code /api/finance/**} {@code .authenticated()} rule in
 * {@link com.example.finance.ledger.infrastructure.security.SecurityConfig} — no extra security
 * configuration is needed.
 *
 * <p><b>Tenant-agnostic:</b> {@code fx_rate_quote} / {@code fx_rate_quote_history} have no
 * {@code tenant_id} column; market rates are global. The authenticated operator receives the
 * full data regardless of tenant.
 */
@RestController
@RequestMapping("/api/finance/ledger/fx-rates")
@RequiredArgsConstructor
public class FxRateController {

    private final GetFxRatesUseCase getFxRates;
    private final GetFxRateHistoryUseCase getFxRateHistory;
    private final RefreshFxRateQuotesUseCase refreshFxRateQuotes;
    private final FxRateFeedSettings fxRateFeedSettings;

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
        ActorContextResolver.currentOrThrow(); // enforce authentication
        FxRatesView view = getFxRates.get();
        return ResponseEntity.ok(ApiEnvelope.of(FxRatesResponse.from(view)));
    }

    /**
     * Return the FX rate history audit trail for one currency pair (27th increment —
     * TASK-FIN-BE-040, ADR-002 history-read drill).
     *
     * <p>Base currency is always the fixed reporting currency (KRW in v1); only the
     * foreign leg is a path variable (mirrors the poller's pairs-are-foreign-legs model).
     * Rows are ordered {@code fetched_at DESC, id DESC} (newest first).
     *
     * <p>Limit normalisation (documented in task AC):
     * <ul>
     *   <li>{@code null} → default 50</li>
     *   <li>{@code ≤ 0} → floored to 1 (read-robustness)</li>
     *   <li>{@code > 500} → capped to 500</li>
     * </ul>
     *
     * <p>An unknown / never-polled pair returns {@code quotes: []} — 200, not 404.
     *
     * @param foreignCurrency ISO-4217 foreign currency code (path variable)
     * @param limit           max rows to return (optional; see normalisation above)
     * @return 200 {@code ApiEnvelope<FxRateHistoryResponse>}; 401/403 when unauthenticated
     */
    @GetMapping("/{foreignCurrency}/history")
    public ResponseEntity<ApiEnvelope<FxRateHistoryResponse>> history(
            @PathVariable String foreignCurrency,
            @RequestParam(required = false) Integer limit) {
        ActorContextResolver.currentOrThrow(); // enforce authentication
        FxRateHistorySummaryView view = getFxRateHistory.get(foreignCurrency, limit);
        return ResponseEntity.ok(ApiEnvelope.of(FxRateHistoryResponse.from(view)));
    }

    /**
     * Trigger an on-demand FX rate cache refresh (TASK-MONO-300 — ADR-002 manual refresh realized).
     *
     * <p>Delegates to {@link RefreshFxRateQuotesUseCase#refresh()} — the same use case
     * the scheduled poller invokes. The upsert is last-write-wins idempotent, so concurrent
     * calls (double-click / concurrent operators / poller tick) are safe.
     *
     * <p><b>Feed disabled</b> ({@code financeplatform.ledger.fxrate.enabled=false}): the
     * noop provider returns {@code Optional.empty()} for every pair; the use case upserts 0
     * rows and returns 0. This endpoint returns 200 {@code {feedEnabled:false, refreshed:0}}
     * — a graceful no-op, NOT an error.
     *
     * <p><b>Provider failures</b>: the use case is best-effort / per-pair try/catch / never-throw;
     * the endpoint returns the count that succeeded and does not 500.
     *
     * @return 200 {@code ApiEnvelope<FxRatesRefreshResponse>}; 401/403 when unauthenticated
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiEnvelope<FxRatesRefreshResponse>> refresh() {
        ActorContextResolver.currentOrThrow(); // enforce authentication
        boolean feedEnabled = fxRateFeedSettings.feedEnabled();
        int refreshed = refreshFxRateQuotes.refresh();
        return ResponseEntity.ok(ApiEnvelope.of(FxRatesRefreshResponse.of(feedEnabled, refreshed)));
    }
}
