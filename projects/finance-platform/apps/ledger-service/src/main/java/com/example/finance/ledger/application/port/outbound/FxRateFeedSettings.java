package com.example.finance.ledger.application.port.outbound;

import java.time.Duration;
import java.util.List;

/**
 * Application-layer view of the FX rate feed settings the load use case needs (23rd increment —
 * TASK-FIN-BE-031, ADR-002). Keeps {@code RefreshFxRateQuotesUseCase} free of any
 * infrastructure import (the layer rule: the application layer must not depend on infrastructure
 * config types). The infrastructure {@code FxRateFeedProperties} implements this so the bound
 * config supplies the value at runtime.
 *
 * <p>24th increment (TASK-FIN-BE-032, ADR-002 D3/D4): extended with {@link #feedEnabled()} +
 * {@link #staleAfter()} so {@code ResolveEffectiveFxRate} can gate the cache fallback (feed
 * disabled → fail-closed) and the staleness guard ({@code now − quote.asOf > staleAfter} →
 * stale → reject). The operator-rate-supplied path never consults either (net-zero).
 */
public interface FxRateFeedSettings {

    /** The foreign-currency legs to poll (base is fixed to KRW). Empty when none configured. */
    List<String> pairs();

    /**
     * Whether the FX rate feed is enabled (the master config gate). When {@code false} the
     * operator's omitted-rate cache fallback is OFF — a settlement/revaluation that omits the rate
     * fails closed ({@code FX_RATE_UNAVAILABLE}) instead of reading a (possibly absent) cache.
     * Default OFF (net-zero — the manual-rate path is unchanged).
     */
    boolean feedEnabled();

    /**
     * The staleness horizon for a cached quote. A quote whose {@code asOf} is older than this
     * relative to now ({@code now − asOf > staleAfter}) is rejected as stale (no estimated-rate
     * P&amp;L; ADR-002 D4). Exactly {@code now − asOf == staleAfter} is FRESH (the boundary is
     * inclusive — see AC-4).
     */
    Duration staleAfter();
}
