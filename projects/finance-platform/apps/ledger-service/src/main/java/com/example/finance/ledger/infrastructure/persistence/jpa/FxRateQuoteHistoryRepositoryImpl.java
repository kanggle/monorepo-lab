package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository;
import lombok.RequiredArgsConstructor;
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
}
