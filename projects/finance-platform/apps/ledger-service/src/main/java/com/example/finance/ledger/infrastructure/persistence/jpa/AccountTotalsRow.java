package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.money.Currency;

/**
 * JPQL projection row for per-account debit/credit totals (trial balance). A
 * constructor-expression target — kept in the infrastructure layer (it is a
 * JPA/query concern, not a domain view).
 */
public record AccountTotalsRow(String ledgerAccountCode, Currency currency,
                               long debitMinor, long creditMinor) {
}
