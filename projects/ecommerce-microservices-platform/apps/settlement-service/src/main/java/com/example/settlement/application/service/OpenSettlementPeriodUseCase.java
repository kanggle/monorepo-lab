package com.example.settlement.application.service;

import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Opens a settlement period (architecture.md § Period close). One
 * {@code @Transactional} boundary: build an OPEN period (the factory validates
 * {@code from < to} → {@link com.example.settlement.domain.period.PeriodWindowInvalidException}
 * → 422 {@code PERIOD_WINDOW_INVALID}) and persist it. The window is operator-supplied
 * and grain-agnostic; no overlap check (a tenant may run overlapping windows — the
 * close folds whichever accruals fall in {@code [from, to)}).
 */
@Service
@RequiredArgsConstructor
public class OpenSettlementPeriodUseCase {

    private final SettlementPeriodRepository periodRepository;

    @Transactional
    public PeriodView open(String tenantId, Instant from, Instant to) {
        SettlementPeriod period = SettlementPeriod.open(
                UUID.randomUUID().toString(), tenantId, from, to);
        SettlementPeriod saved = periodRepository.save(period);
        return PeriodView.summary(saved);
    }
}
