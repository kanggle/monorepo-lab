package com.example.finance.ledger.domain.reconciliation.repository;

import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.InternalLine;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for reconciliation persistence (architecture.md § Layer
 * Structure, 4th increment). Statements/lines/matches are insert-only;
 * discrepancies transition OPEN→RESOLVED via the operator use case. Reads are
 * tenant-scoped. Implemented by an infrastructure JPA adapter.
 */
public interface ReconciliationRepository {

    /** Persist the statement + its lines (cascade). */
    ExternalStatement saveStatement(ExternalStatement statement);

    Optional<ExternalStatement> findStatementById(String statementId, String tenantId);

    /** Persist the recorded matches (insert-only). */
    void saveMatches(List<ReconciliationMatch> matches);

    /** Persist the recorded discrepancies (insert-only, always OPEN at creation). */
    List<ReconciliationDiscrepancy> saveDiscrepancies(List<ReconciliationDiscrepancy> discrepancies);

    /** Save one discrepancy (used by the operator resolve path). */
    ReconciliationDiscrepancy saveDiscrepancy(ReconciliationDiscrepancy discrepancy);

    Optional<ReconciliationDiscrepancy> findDiscrepancyById(String discrepancyId, String tenantId);

    /** The matches recorded for one statement (its line ids), for the statement detail read. */
    List<ReconciliationMatch> findMatchesByStatement(String statementId, String tenantId);

    /** The discrepancies recorded for one statement, for the statement detail read. */
    List<ReconciliationDiscrepancy> findDiscrepanciesByStatement(String statementId, String tenantId);

    /**
     * The discrepancy review queue, tenant-scoped, optionally filtered by status,
     * most-recent first, paginated.
     */
    DiscrepancyPage findDiscrepancies(String tenantId, DiscrepancyStatus status,
                                      int page, int size);

    /**
     * The internal journal lines on {@code ledgerAccountCode} (tenant-scoped)
     * whose owning entry is NOT already in {@code reconciliation_match} — the
     * candidate internal lines for matching (the anti-join against prior matches).
     */
    List<InternalLine> findUnmatchedInternalLines(String tenantId, String ledgerAccountCode);

    /** A page of discrepancies (the review queue). */
    record DiscrepancyPage(List<ReconciliationDiscrepancy> content, int page, int size,
                           long totalElements, int totalPages) {
    }
}
