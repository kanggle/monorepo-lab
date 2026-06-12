package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.money.Currency;

/**
 * JPQL projection row for per-account debit/credit totals (trial balance). A
 * constructor-expression target — kept in the infrastructure layer (it is a
 * JPA/query concern, not a domain view).
 *
 * <p>(8th incr) Each row (per {@code (account, currency)}) carries both the original
 * transaction-currency sums ({@code debitMinor}/{@code creditMinor}) AND the
 * base-currency (KRW) consolidated sums ({@code baseDebitMinor}/{@code baseCreditMinor}).
 * The base sums are all KRW regardless of the line's currency, so summing them across
 * rows yields the base-currency grand total.
 */
public record AccountTotalsRow(String ledgerAccountCode, Currency currency,
                               long debitMinor, long creditMinor,
                               long baseDebitMinor, long baseCreditMinor) {
}
