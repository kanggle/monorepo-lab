package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.application.view.FxRateView;
import com.example.finance.ledger.application.view.FxRatesView;
import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Read the current FX rate quote cache and expose each quote's staleness to the
 * operator (25th increment — TASK-FIN-BE-033, ADR-002 D2 read surface).
 *
 * <p>Staleness boundary is <em>identical</em> to
 * {@link ResolveEffectiveFxRate}: {@code now − asOf > staleAfter} → stale;
 * {@code now − asOf == staleAfter} → <strong>fresh</strong> (inclusive boundary,
 * AC-3). This prevents operator visibility drifting from the settlement / revaluation
 * gate (FIN-BE-032 alignment).
 *
 * <p>{@code fx_rate_quote} has no {@code tenant_id} column — market rates are
 * tenant-agnostic globals. The controller enforces authentication via
 * {@link com.example.finance.ledger.infrastructure.security.ActorContextResolver#currentOrThrow()};
 * no tenant filter is applied here (AC-5).
 *
 * <p>Quotes are returned sorted {@code (baseCurrency, foreignCurrency)} ASC for
 * deterministic display (AC-2). Empty cache → empty list with {@code feedEnabled}
 * from settings (AC-1 — 200, not 404). {@code ageSeconds} may be negative on clock
 * skew (edge case documented in the task — no clamping, transparency wins).
 */
@Service
@RequiredArgsConstructor
public class GetFxRatesUseCase {

    private final FxRateQuoteRepository fxRateQuoteRepository;
    private final FxRateFeedSettings settings;
    private final ClockPort clock;

    /**
     * Return all cached FX rate quotes with computed staleness fields.
     *
     * @return view containing {@code feedEnabled} + the sorted, annotated quote list
     */
    @Transactional(readOnly = true)
    public FxRatesView get() {
        Instant now = clock.now();
        boolean feedEnabled = settings.feedEnabled();
        Duration staleAfter = settings.staleAfter();

        List<FxRateView> rates = fxRateQuoteRepository.findAll().stream()
                .sorted(Comparator.comparing(FxRateQuote::baseCurrency)
                        .thenComparing(FxRateQuote::foreignCurrency))
                .map(q -> {
                    Duration age = Duration.between(q.asOf(), now);
                    long ageSeconds = age.getSeconds();
                    // now − asOf > staleAfter → stale; == staleAfter is fresh (AC-3).
                    boolean stale = age.compareTo(staleAfter) > 0;
                    return new FxRateView(
                            q.baseCurrency(),
                            q.foreignCurrency(),
                            q.rate(),
                            q.asOf(),
                            q.source(),
                            q.fetchedAt(),
                            ageSeconds,
                            stale);
                })
                .toList();

        return new FxRatesView(feedEnabled, rates);
    }
}
