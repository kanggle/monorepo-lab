package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort;
import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Refresh the FX rate quote cache (23rd increment — TASK-FIN-BE-031, ADR-002 D2/D4). For each
 * configured foreign-currency leg ({@code financeplatform.ledger.fxrate.pairs}; base is fixed to
 * KRW = {@link LedgerReportingCurrency#BASE}) it calls {@link FxRateProviderPort#latestQuote} and
 * upserts the result into {@code fx_rate_quote} (last-write-wins, {@code fetched_at=clock.now()}).
 *
 * <p><b>Per-pair isolation</b> (AC-6): an unparseable currency code or a single failing pair does
 * NOT abort the others — each is processed in its own try/catch. An empty provider result (absent /
 * unsupported pair / noop default) is skipped (partial cache load is allowed).
 *
 * <p><b>Shadow / net-zero</b>: this only LOADS the cache; no operator path
 * ({@code SettleForeignPositionUseCase} / {@code RevalueForeignBalanceUseCase}) reads it in this
 * increment. With the default config ({@code mode=noop}, {@code enabled=false}) the poller bean is
 * not even created, so this use case is never invoked and the cache stays empty.
 *
 * <p><b>History trail</b> (26th increment — TASK-FIN-BE-039, ADR-002 § 3.1 item 3): after each
 * latest-upsert into {@code fx_rate_quote}, the same quote is <em>also</em> appended to
 * {@code fx_rate_quote_history} (append-only — many rows per pair over time) inside the SAME
 * {@code @Transactional} and the SAME per-pair try/catch. The existing upsert call is
 * byte-unchanged; history is purely additive.
 */
@Service
@RequiredArgsConstructor
public class RefreshFxRateQuotesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshFxRateQuotesUseCase.class);

    private final FxRateProviderPort fxRateProviderPort;
    private final FxRateQuoteRepository fxRateQuoteRepository;
    private final FxRateQuoteHistoryRepository fxRateQuoteHistoryRepository;
    private final FxRateFeedSettings settings;
    private final ClockPort clock;

    /**
     * Poll every configured pair and upsert the present results.
     *
     * @return the number of pairs upserted this run.
     */
    @Transactional
    public int refresh() {
        Currency base = LedgerReportingCurrency.BASE;
        int upserted = 0;
        for (String foreignCode : settings.pairs()) {
            try {
                Currency foreign = Currency.of(foreignCode);
                var quote = fxRateProviderPort.latestQuote(base, foreign);
                if (quote.isEmpty()) {
                    continue;
                }
                Instant now = clock.now();
                fxRateQuoteRepository.save(FxRateQuote.of(
                        base, foreign,
                        quote.get().rate(),
                        quote.get().asOf(),
                        quote.get().source(),
                        now));
                fxRateQuoteHistoryRepository.append(FxRateQuoteHistory.of(
                        base, foreign,
                        quote.get().rate(),
                        quote.get().asOf(),
                        quote.get().source(),
                        now));
                upserted++;
            } catch (Exception e) {
                // One pair's failure (unparseable code, provider error, persist hiccup) must not
                // block the rest — log and continue (AC-6).
                log.warn("FX_RATE_REFRESH_PAIR_FAILED pair={}", foreignCode, e);
            }
        }
        return upserted;
    }
}
