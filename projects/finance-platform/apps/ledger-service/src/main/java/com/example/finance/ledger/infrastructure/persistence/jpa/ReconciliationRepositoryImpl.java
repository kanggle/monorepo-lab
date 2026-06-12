package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.ExternalStatementLine;
import com.example.finance.ledger.domain.reconciliation.InternalLine;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link ReconciliationRepository}. */
@Component
@RequiredArgsConstructor
public class ReconciliationRepositoryImpl implements ReconciliationRepository {

    private final ReconciliationStatementJpaRepository statementJpa;
    private final ReconciliationStatementLineJpaRepository lineJpa;
    private final ReconciliationMatchJpaRepository matchJpa;
    private final ReconciliationDiscrepancyJpaRepository discrepancyJpa;
    private final JournalLineJpaRepository journalLineJpa;

    @Override
    public ExternalStatement saveStatement(ExternalStatement statement) {
        return statementJpa.save(statement);
    }

    @Override
    public Optional<ExternalStatement> findStatementById(String statementId, String tenantId) {
        return statementJpa.findByStatementIdAndTenantId(statementId, tenantId);
    }

    @Override
    public void saveMatches(List<ReconciliationMatch> matches) {
        if (!matches.isEmpty()) {
            matchJpa.saveAll(matches);
        }
    }

    @Override
    public List<ReconciliationDiscrepancy> saveDiscrepancies(
            List<ReconciliationDiscrepancy> discrepancies) {
        if (discrepancies.isEmpty()) {
            return List.of();
        }
        return discrepancyJpa.saveAll(discrepancies);
    }

    @Override
    public ReconciliationDiscrepancy saveDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        return discrepancyJpa.save(discrepancy);
    }

    @Override
    public Optional<ReconciliationDiscrepancy> findDiscrepancyById(String discrepancyId,
                                                                   String tenantId) {
        return discrepancyJpa.findByDiscrepancyIdAndTenantId(discrepancyId, tenantId);
    }

    @Override
    public List<ReconciliationMatch> findMatchesByStatement(String statementId, String tenantId) {
        List<String> lineIds = lineJpa.findByStatementIdAndTenantId(statementId, tenantId).stream()
                .map(ExternalStatementLine::lineId).toList();
        if (lineIds.isEmpty()) {
            return List.of();
        }
        return matchJpa.findByTenantIdAndStatementLineIdIn(tenantId, lineIds);
    }

    @Override
    public List<ReconciliationDiscrepancy> findDiscrepanciesByStatement(String statementId,
                                                                        String tenantId) {
        return discrepancyJpa.findByStatementIdAndTenantId(statementId, tenantId);
    }

    @Override
    public DiscrepancyPage findDiscrepancies(String tenantId, DiscrepancyStatus status,
                                             int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<ReconciliationDiscrepancy> p = status == null
                ? discrepancyJpa.findByTenantIdOrderByDetectedAtDescDiscrepancyIdDesc(
                        tenantId, pageable)
                : discrepancyJpa.findByTenantIdAndStatusOrderByDetectedAtDescDiscrepancyIdDesc(
                        tenantId, status, pageable);
        return new DiscrepancyPage(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }

    @Override
    public List<InternalLine> findUnmatchedInternalLines(String tenantId, String ledgerAccountCode) {
        return journalLineJpa.findUnmatchedInternalLines(tenantId, ledgerAccountCode).stream()
                .map(this::toInternalLine)
                .toList();
    }

    private InternalLine toInternalLine(JournalLine line) {
        return new InternalLine(line.entryId(), line.ledgerAccountCode(),
                line.direction(), line.money());
    }
}
