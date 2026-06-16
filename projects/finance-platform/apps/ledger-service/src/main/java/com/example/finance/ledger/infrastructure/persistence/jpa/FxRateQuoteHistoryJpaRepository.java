package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the FX rate quote history audit trail (surrogate PK {@code id}
 * — append-only; many rows per {@code (base_currency, foreign_currency)} pair).
 */
public interface FxRateQuoteHistoryJpaRepository extends JpaRepository<FxRateQuoteHistory, Long> {
}
