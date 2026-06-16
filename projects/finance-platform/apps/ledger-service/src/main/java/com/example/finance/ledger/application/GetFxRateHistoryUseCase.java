package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxRateHistorySummaryView;
import com.example.finance.ledger.application.view.FxRateHistoryView;
import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Return the FX rate history audit trail for one currency pair (27th increment —
 * TASK-FIN-BE-040, ADR-002 § 3.1 history-read drill).
 *
 * <p>Base currency is always {@link LedgerReportingCurrency#BASE} (KRW in v1);
 * only the foreign leg is caller-supplied. Limit is normalised before the query:
 * <ul>
 *   <li>{@code null} → default 50</li>
 *   <li>{@code ≤ 0} → floor 1 (read-robustness: never reject a bounded request)</li>
 *   <li>{@code > 500} → cap 500 (guards against unbounded payload on high-cadence pairs)</li>
 * </ul>
 *
 * <p>An unknown or unsupported foreign currency code returns an empty list (200) —
 * {@link Currency#of(String)} throws {@link Currency.UnsupportedCurrencyException} on an
 * unknown code (incl. well-formed 3-letter codes not in the supported set); this use-case
 * catches that and returns empty, consistent with the empty-200 stance of the list EP
 * (FIN-BE-033 AC-1). A never-polled pair likewise returns empty (the repository returns an
 * empty list when no rows exist for the pair).
 *
 * <p>No write path, no idempotency key, no migration — pure read / net-zero.
 */
@Service
@RequiredArgsConstructor
public class GetFxRateHistoryUseCase {

    static final int DEFAULT_LIMIT = 50;
    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 500;

    private final FxRateQuoteHistoryRepository fxRateQuoteHistoryRepository;

    /**
     * Fetch the per-pair history, newest first.
     *
     * @param foreignCode ISO-4217 foreign currency code (path variable); unknown code → empty list
     * @param rawLimit    caller-supplied limit; {@code null} uses the default (50); normalised per above
     * @return aggregated view with {@code baseCurrency}, {@code foreignCurrency}, and {@code quotes}
     *         (newest first; may be empty)
     */
    @Transactional(readOnly = true)
    public FxRateHistorySummaryView get(String foreignCode, Integer rawLimit) {
        Currency base = LedgerReportingCurrency.BASE;
        int limit = normaliseLimit(rawLimit);

        Currency foreign;
        try {
            foreign = Currency.of(foreignCode);
        } catch (Currency.UnsupportedCurrencyException | NullPointerException e) {
            // Unknown / unsupported / null code → empty-200 (mirrors the list EP's stance).
            String displayCode = (foreignCode != null) ? foreignCode.trim().toUpperCase() : "";
            return new FxRateHistorySummaryView(base.code(), displayCode, List.of());
        }

        List<FxRateQuoteHistory> rows =
                fxRateQuoteHistoryRepository.findHistory(base, foreign, limit);

        List<FxRateHistoryView> quotes = rows.stream()
                .map(r -> new FxRateHistoryView(r.rate(), r.asOf(), r.fetchedAt(), r.source()))
                .toList();

        return new FxRateHistorySummaryView(base.code(), foreign.code(), quotes);
    }

    /**
     * Normalise the caller-supplied limit per the documented policy:
     * {@code null} → 50; {@code ≤ 0} → 1; {@code > 500} → 500.
     */
    static int normaliseLimit(Integer raw) {
        if (raw == null) {
            return DEFAULT_LIMIT;
        }
        if (raw <= 0) {
            return MIN_LIMIT;
        }
        if (raw > MAX_LIMIT) {
            return MAX_LIMIT;
        }
        return raw;
    }
}
