package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for the FX rate quote history audit trail (surrogate PK {@code id}
 * — append-only; many rows per {@code (base_currency, foreign_currency)} pair).
 */
public interface FxRateQuoteHistoryJpaRepository extends JpaRepository<FxRateQuoteHistory, Long> {

    /**
     * Fetch the most-recent history rows for a {@code (baseCurrency, foreignCurrency)} pair,
     * ordered {@code fetched_at DESC, id DESC} (deterministic tie-break).
     *
     * <p>Spring Data derives the query from the method name;
     * {@code Pageable} limits the result set (the domain port translates a plain {@code int limit}
     * to {@link org.springframework.data.domain.PageRequest#of(int, int)} in the adapter).
     *
     * @param baseCurrency    3-letter ISO-4217 base code (e.g. {@code "KRW"})
     * @param foreignCurrency 3-letter ISO-4217 foreign code (e.g. {@code "USD"})
     * @param pageable        page 0 of size = limit with sort {@code (fetchedAt DESC, id DESC)}
     * @return matched rows in newest-first order; empty when the pair was never polled
     */
    List<FxRateQuoteHistory> findByBaseCurrencyAndForeignCurrencyOrderByFetchedAtDescIdDesc(
            String baseCurrency, String foreignCurrency, Pageable pageable);
}
