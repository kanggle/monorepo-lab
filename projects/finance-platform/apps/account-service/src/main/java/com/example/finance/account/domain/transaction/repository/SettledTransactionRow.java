package com.example.finance.account.domain.transaction.repository;

import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.status.TransactionStatus;
import com.example.finance.common.money.Currency;

import java.time.Instant;

/**
 * One {@code transactions} row whose {@code settled_at} falls inside a metrics window
 * (TASK-MONO-812, ADR-MONO-084 P7) — its kind, its <em>current</em> status, its amount in
 * integer minor units of its own currency, and the settle instant it is attributed to.
 *
 * <p>Deliberately carries <b>no account id, counterparty or hold id</b>: the metrics read is a
 * tenant-wide total, and {@code account-api.md} keeps the account set unlisted (§ GET
 * /accounts — deliberately absent). A field that is not selected cannot leak into the response.
 * The status and type are carried so the application service — not the query — applies the
 * inclusion rule ({@code account-api.md} § GET /api/finance/accounts/metrics).
 */
public record SettledTransactionRow(
        TransactionType type,
        TransactionStatus status,
        long amountMinor,
        Currency currency,
        Instant settledAt) {
}
