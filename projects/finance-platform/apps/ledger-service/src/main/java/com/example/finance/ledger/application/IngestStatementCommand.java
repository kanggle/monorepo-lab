package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.reconciliation.StatementSource;

import java.time.LocalDate;
import java.util.List;

/**
 * Command to ingest an external statement (architecture.md § Reconciliation). The
 * controller maps the validated request body to this; the use case turns the
 * lines into a domain {@code ExternalStatement}. Money is already a resolved
 * {@link Money} value object (minor units, F5).
 */
public record IngestStatementCommand(
        String tenantId,
        String ledgerAccountCode,
        StatementSource source,
        LocalDate statementDate,
        List<Line> lines,
        String actor) {

    /** One ingest line (the request body's line shape, parsed). */
    public record Line(String externalRef, Money money, EntryDirection direction,
                       LocalDate valueDate, String description) {
    }
}
