package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.money.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command to settle one {@code (ledgerAccountCode, currency)} foreign position at a
 * settlement (spot) rate (10th increment, TASK-FIN-BE-016 — architecture.md § FX
 * settlement). The controller maps the validated request body + the
 * {@code Idempotency-Key} header + the resolved actor to this; the use case loads the
 * position, computes the realized gain/loss via {@code FxSettlementPolicy}, and funnels
 * a balanced base-currency (KRW) 3-line entry through the single guarded write path.
 *
 * <p>Money stays integer minor units (F5); only {@code settlementRate} is an exact
 * {@link BigDecimal} (the base-minor-per-foreign-minor spot factor, strictly positive).
 *
 * <p><b>12th increment (TASK-FIN-BE-018) — partial settlement.</b> The optional
 * {@code settleForeignMinor} settles only a <b>portion</b> {@code F_settle} of the
 * position (same sign as the position's foreign balance {@code F}, {@code 0 < |F_settle|
 * ≤ |F|}); {@code null} settles the <b>whole</b> position byte-identically to the 10th
 * increment (net-zero). The use case validates it (zero / opposite-sign / over-settle →
 * {@code SETTLEMENT_AMOUNT_INVALID}).
 *
 * @param tenantId            the operator's tenant (row-level isolation)
 * @param operatorSubject     the JWT subject recorded as the audit actor
 * @param ledgerAccountCode   the foreign account whose position is settled (removed)
 * @param currency            the position's foreign currency (must not be base/KRW)
 * @param settlementRate      the settlement (spot) rate (base-minor-per-foreign-minor, > 0)
 * @param proceedsAccountCode the base-currency account that receives/pays the proceeds
 *                            (must already exist — no lazy mint)
 * @param settleForeignMinor  optional partial-settlement portion {@code F_settle} (foreign
 *                            minor, same sign as {@code F}); {@code null} = full settlement
 * @param postedAt            optional effective instant — {@code null} defaults to now
 * @param reference           optional operator narrative — the entry's
 *                            {@code source.sourceTransactionId} + part of the audit reason
 * @param memo                optional operator narrative — the audit reason
 * @param idempotencyKey      the client {@code Idempotency-Key} (namespaced
 *                            {@code settle:{key}} into the dedupe store, F1)
 */
public record SettleForeignPositionCommand(
        String tenantId,
        String operatorSubject,
        String ledgerAccountCode,
        Currency currency,
        BigDecimal settlementRate,
        String proceedsAccountCode,
        Long settleForeignMinor,
        Instant postedAt,
        String reference,
        String memo,
        String idempotencyKey) {
}
