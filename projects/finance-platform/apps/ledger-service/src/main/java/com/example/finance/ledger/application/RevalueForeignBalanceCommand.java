package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.money.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command to revalue one {@code (ledgerAccountCode, currency)} foreign position at a
 * closing (spot) rate (9th increment, TASK-FIN-BE-015 — architecture.md § FX
 * gain/loss revaluation). The controller maps the validated request body + the
 * {@code Idempotency-Key} header + the resolved actor to this; the use case loads the
 * position, computes the carrying delta via {@code FxRevaluationPolicy}, and funnels a
 * balanced base-currency (KRW) adjusting entry through the single guarded write path.
 *
 * <p>Money stays integer minor units (F5); only {@code closingRate} is an exact
 * {@link BigDecimal} (the base-minor-per-foreign-minor spot factor, strictly positive).
 *
 * @param tenantId          the operator's tenant (row-level isolation)
 * @param operatorSubject   the JWT subject recorded as the audit actor
 * @param ledgerAccountCode the foreign account whose carrying is trued to spot
 * @param currency          the position's foreign currency (must not be base/KRW)
 * @param closingRate       the closing (spot) rate (base-minor-per-foreign-minor, > 0)
 * @param postedAt          optional effective instant — {@code null} defaults to now
 *                          (a month-end effective instant)
 * @param reference         optional operator narrative — the entry's
 *                          {@code source.sourceTransactionId} + part of the audit reason
 * @param memo              optional operator narrative — the audit reason
 * @param idempotencyKey    the client {@code Idempotency-Key} (namespaced
 *                          {@code reval:{key}} into the dedupe store, F1)
 */
public record RevalueForeignBalanceCommand(
        String tenantId,
        String operatorSubject,
        String ledgerAccountCode,
        Currency currency,
        BigDecimal closingRate,
        Instant postedAt,
        String reference,
        String memo,
        String idempotencyKey) {
}
