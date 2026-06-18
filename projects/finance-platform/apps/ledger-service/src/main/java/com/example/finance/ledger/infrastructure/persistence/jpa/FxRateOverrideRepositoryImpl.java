package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateOverride;
import com.example.finance.ledger.domain.journal.FxRateOverrideId;
import com.example.finance.ledger.domain.journal.repository.FxRateOverrideRepository;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** JPA adapter for {@link FxRateOverrideRepository}. */
@Component
@RequiredArgsConstructor
public class FxRateOverrideRepositoryImpl implements FxRateOverrideRepository {

    private final FxRateOverrideJpaRepository jpa;

    @Override
    public Optional<FxRateOverride> findOverride(String tenantId, Currency base, Currency foreign) {
        return jpa.findById(new FxRateOverrideId(tenantId, base.code(), foreign.code()));
    }

    @Override
    public FxRateOverride save(FxRateOverride override) {
        return jpa.save(override);
    }
}
