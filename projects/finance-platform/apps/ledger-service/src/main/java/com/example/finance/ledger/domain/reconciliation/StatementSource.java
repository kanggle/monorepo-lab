package com.example.finance.ledger.domain.reconciliation;

/**
 * The external system an {@link ExternalStatement} originates from
 * (architecture.md § Reconciliation). A bank settlement file, a PG (payment
 * gateway) settlement, or an unspecified other source. Pure Java.
 */
public enum StatementSource {
    BANK,
    PG,
    OTHER
}
