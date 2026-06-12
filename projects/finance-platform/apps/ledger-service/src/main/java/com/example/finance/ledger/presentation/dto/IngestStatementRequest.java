package com.example.finance.ledger.presentation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /reconciliation/statements request (reconciliation-api.md § 1). {@code
 * source} ∈ {BANK, PG, OTHER}; each line carries a money minor-string + direction
 * relative to the reconciled clearing account. The controller resolves {@code
 * source} / {@code direction} / {@code currency} to domain enums (an unknown value
 * surfaces as a 4xx via the {@code GlobalExceptionHandler}).
 */
public record IngestStatementRequest(
        String ledgerAccountCode,
        String source,
        LocalDate statementDate,
        List<Line> lines) {

    /**
     * One ingest line — externalRef + money (minor string) + direction + value date.
     * (11th incr — TASK-FIN-BE-017, multi-currency) {@code baseAmount} is optional:
     * the bank-reported base/KRW value for a foreign-currency line, omitted for a KRW
     * / base-less line.
     */
    public record Line(String externalRef, MoneyRequest money, String direction,
                       LocalDate valueDate, String description, MoneyRequest baseAmount) {
    }
}
