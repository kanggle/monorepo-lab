package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/** JPA adapter for {@link FxRateQuoteHistoryRepository}. */
@Component
@RequiredArgsConstructor
public class FxRateQuoteHistoryRepositoryImpl implements FxRateQuoteHistoryRepository {

    private final FxRateQuoteHistoryJpaRepository jpa;

    @Override
    public FxRateQuoteHistory append(FxRateQuoteHistory history) {
        return jpa.save(history);
    }

    @Override
    public List<FxRateQuoteHistory> findAll() {
        return jpa.findAll();
    }

    /**
     * (27th increment — TASK-FIN-BE-040) Fetch the most-recent {@code limit} history rows
     * for a currency pair, newest first. The domain port takes a plain {@code int limit};
     * this adapter translates it to a {@link PageRequest} (page 0, size = limit) — keeping
     * the domain port Spring-free. The Spring Data derived query orders by
     * {@code fetched_at DESC, id DESC} for deterministic tie-breaking.
     */
    @Override
    public List<FxRateQuoteHistory> findHistory(Currency base, Currency foreign, int limit) {
        return jpa.findByBaseCurrencyAndForeignCurrencyOrderByFetchedAtDescIdDesc(
                base.code(), foreign.code(), PageRequest.of(0, limit));
    }
}
