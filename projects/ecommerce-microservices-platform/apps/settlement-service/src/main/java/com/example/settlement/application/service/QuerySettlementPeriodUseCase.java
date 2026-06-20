package com.example.settlement.application.service;

import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side settlement-period use case (settlement-api.md § GET /periods). Lists the
 * tenant's periods (most recent first) and fetches one period. Reads are
 * tenant-scoped and side-effect free; a cross-tenant {@code periodId} →
 * {@link PeriodNotFoundException} (404, M3).
 */
@Service
@RequiredArgsConstructor
public class QuerySettlementPeriodUseCase {

    private final SettlementPeriodRepository periodRepository;

    @Transactional(readOnly = true)
    public List<PeriodView> listPeriods(String tenantId) {
        return periodRepository.findAll(tenantId).stream()
                .map(PeriodView::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PeriodView getPeriod(String periodId, String tenantId) {
        SettlementPeriod period = periodRepository.findById(periodId, tenantId)
                .orElseThrow(() -> new PeriodNotFoundException(
                        "settlement period not found: " + periodId));
        return PeriodView.summary(period);
    }
}
