package com.example.settlement.application.service;

import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side settlement-period use case (settlement-api.md § GET /periods). Lists the
 * tenant's periods (most recent first). Reads are tenant-scoped and side-effect free.
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
}
