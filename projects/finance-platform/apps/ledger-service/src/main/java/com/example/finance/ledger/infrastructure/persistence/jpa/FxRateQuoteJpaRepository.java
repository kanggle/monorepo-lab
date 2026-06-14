package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.FxRateQuoteId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the FX rate quote cache (composite PK
 * {@code (base_currency, foreign_currency)} via {@link FxRateQuoteId}).
 */
public interface FxRateQuoteJpaRepository extends JpaRepository<FxRateQuote, FxRateQuoteId> {
}
