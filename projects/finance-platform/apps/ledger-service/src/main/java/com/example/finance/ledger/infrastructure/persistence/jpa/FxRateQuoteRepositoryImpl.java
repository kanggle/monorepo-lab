package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.FxRateQuoteId;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteRepository;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link FxRateQuoteRepository}. */
@Component
@RequiredArgsConstructor
public class FxRateQuoteRepositoryImpl implements FxRateQuoteRepository {

    private final FxRateQuoteJpaRepository jpa;

    @Override
    public Optional<FxRateQuote> findLatest(Currency base, Currency foreign) {
        return jpa.findById(new FxRateQuoteId(base.code(), foreign.code()));
    }

    @Override
    public FxRateQuote save(FxRateQuote quote) {
        return jpa.save(quote);
    }

    @Override
    public List<FxRateQuote> findAll() {
        return jpa.findAll();
    }
}
