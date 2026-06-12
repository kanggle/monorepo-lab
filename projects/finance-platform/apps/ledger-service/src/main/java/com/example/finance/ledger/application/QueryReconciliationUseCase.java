package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.DiscrepancyPageView;
import com.example.finance.ledger.application.view.DiscrepancyView;
import com.example.finance.ledger.application.view.StatementView;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationDiscrepancyNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationStatementNotFoundException;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository.DiscrepancyPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side reconciliation use case (reconciliation-api.md § 3/§ 4/§ 5): statement
 * detail + its matches/discrepancies; the discrepancy review queue (filtered by
 * status, paginated); discrepancy detail. Reads are tenant-scoped and
 * side-effect free.
 */
@Service
@RequiredArgsConstructor
public class QueryReconciliationUseCase {

    private final ReconciliationRepository reconciliationRepository;

    @Transactional(readOnly = true)
    public StatementView getStatement(String statementId, String tenantId) {
        ExternalStatement statement =
                reconciliationRepository.findStatementById(statementId, tenantId)
                        .orElseThrow(() -> new ReconciliationStatementNotFoundException(
                                "reconciliation statement not found: " + statementId));
        List<ReconciliationMatch> matches =
                reconciliationRepository.findMatchesByStatement(statementId, tenantId);
        List<ReconciliationDiscrepancy> discrepancies =
                reconciliationRepository.findDiscrepanciesByStatement(statementId, tenantId);
        return StatementView.of(statement, matches, discrepancies);
    }

    @Transactional(readOnly = true)
    public DiscrepancyPageView listDiscrepancies(String tenantId, DiscrepancyStatus status,
                                                 int page, int size) {
        DiscrepancyPage p = reconciliationRepository.findDiscrepancies(tenantId, status, page, size);
        List<DiscrepancyView> content = p.content().stream().map(DiscrepancyView::from).toList();
        return new DiscrepancyPageView(content, p.page(), p.size(),
                p.totalElements(), p.totalPages());
    }

    @Transactional(readOnly = true)
    public DiscrepancyView getDiscrepancy(String discrepancyId, String tenantId) {
        ReconciliationDiscrepancy discrepancy =
                reconciliationRepository.findDiscrepancyById(discrepancyId, tenantId)
                        .orElseThrow(() -> new ReconciliationDiscrepancyNotFoundException(
                                "reconciliation discrepancy not found: " + discrepancyId));
        return DiscrepancyView.from(discrepancy);
    }
}
