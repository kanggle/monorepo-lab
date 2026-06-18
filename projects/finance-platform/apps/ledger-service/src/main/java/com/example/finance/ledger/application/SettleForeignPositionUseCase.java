package com.example.finance.ledger.application;

import com.example.finance.ledger.application.ResolveEffectiveFxRate.ResolvedFxRate;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.SettlementAmountInvalidException;
import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfig;
import com.example.finance.ledger.domain.journal.FxCostFlowConfig;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy.SettlementResult;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowAccountConfigRepository;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowConfigRepository;
import com.example.finance.ledger.domain.journal.repository.FxPositionLotRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository.AccountTotals;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Realized FX gain/loss on settlement use case (10th increment, TASK-FIN-BE-016 —
 * architecture.md § FX settlement). An operator settles a {@code (ledgerAccountCode,
 * currency)} foreign position at a settlement (spot) rate, removing the position at
 * its carrying value and recognising the realized difference between the base proceeds
 * and the carrying as {@code FX_GAIN}/{@code FX_LOSS}. Like revaluation (9th increment)
 * it adds <b>no new write boundary</b> — it builds a balanced base-currency (KRW)
 * 3-line entry via {@link FxSettlementPolicy} and funnels it through the existing
 * {@link PostJournalEntryUseCase#post(JournalEntry, String, String)} (the single guarded
 * write path), inheriting the closed-period guard, the audit row (actor = the operator
 * subject), and the {@code entry.posted} outbox append.
 *
 * <p>Idempotent (F1) on a client {@code Idempotency-Key} namespaced {@code settle:{key}}.
 * A replay returns the original entry (no re-post). A no-op (no position) returns
 * {@code settled=false} and does <b>not</b> consume the key (net-zero — a real position
 * can be settled later). Unlike revaluation there is no {@code AT_SPOT} no-op — a
 * settlement always books when there is a position, even when {@code realized == 0}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettleForeignPositionUseCase {

    static final String DEDUPE_PREFIX = "settle:";
    static final String DEDUPE_TOPIC = "fx-settlement";

    private final JournalRepository journalRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final ProcessedEventStore processedEventStore;
    private final PostJournalEntryUseCase postJournalEntryUseCase;
    private final FxCostFlowConfigRepository fxCostFlowConfigRepository;
    private final FxCostFlowAccountConfigRepository fxCostFlowAccountConfigRepository;
    private final FxPositionLotRepository fxPositionLotRepository;
    private final ResolveEffectiveFxRate fxRateResolver;
    private final ClockPort clock;

    /** Why a settlement booked nothing ({@code settled=false}). */
    public enum NoOpReason {
        REPLAY, NO_POSITION
    }

    /**
     * The settlement outcome. {@code settled=true} → an entry was booked (the
     * controller maps it to 201) with the signed {@code realizedBaseMinor} + the
     * {@code proceedsBaseMinor} + the FX {@code outcome} + the residual OPEN position
     * {@code (residualForeignMinor, residualCarryingBaseMinor)} that remains after a
     * partial settle (both {@code 0} on a full settle — 12th increment,
     * TASK-FIN-BE-018); {@code settled=false} → a no-op/replay (200) carrying the
     * {@link NoOpReason} and the original entry on a replay (else {@code null}).
     */
    public record Result(boolean settled, long realizedBaseMinor, long proceedsBaseMinor,
                         FxSettlementPolicy.Outcome outcome, long residualForeignMinor,
                         long residualCarryingBaseMinor, NoOpReason reason,
                         JournalEntry entry) {

        static Result settled(SettlementResult r, long residualForeignMinor,
                              long residualCarryingBaseMinor, JournalEntry entry) {
            return new Result(true, r.realized(), r.proceedsBase(), r.outcome(),
                    residualForeignMinor, residualCarryingBaseMinor, null, entry);
        }

        static Result noOp(NoOpReason reason, JournalEntry entry) {
            return new Result(false, 0L, 0L, null, 0L, 0L, reason, entry);
        }
    }

    @Transactional
    public Result settle(SettleForeignPositionCommand cmd) {
        String key = cmd.idempotencyKey();
        LedgerWriteSupport.validateIdempotencyKey(key);
        String dedupeKey = DEDUPE_PREFIX + key;

        // (1) Idempotent replay — the key was already processed; return the original
        //     entry (no re-post). The unique constraint on processed_events makes a
        //     concurrent double-submit race-safe (the loser lands here).
        if (processedEventStore.isProcessed(dedupeKey)) {
            JournalEntry original = LedgerWriteSupport.requireReplayEntry(
                    journalRepository, dedupeKey, cmd.tenantId(), "settlement");
            return Result.noOp(NoOpReason.REPLAY, original);
        }

        // (2) The base currency has no FX position to settle (CURRENCY_MISMATCH).
        if (cmd.currency() == LedgerReportingCurrency.BASE) {
            throw new CurrencyMismatchException(
                    "the base currency (" + LedgerReportingCurrency.BASE
                            + ") has no FX position to settle");
        }

        // (3) The proceeds account must already exist — an operator settles into an
        //     existing account (no lazy mint, like manual posting).
        if (!ledgerAccountRepository.existsByCode(cmd.proceedsAccountCode(), cmd.tenantId())) {
            throw new LedgerAccountNotFoundException(
                    "proceeds account does not exist: " + cmd.proceedsAccountCode());
        }

        // (4) Load the position. No row / zero foreign balance → 200 settled:false
        //     (net-zero; the key is NOT marked — a real position can be settled later).
        Optional<AccountTotals> totals = journalRepository.accountTotalsForCurrency(
                cmd.ledgerAccountCode(), cmd.currency(), cmd.tenantId());
        if (totals.isEmpty()) {
            return Result.noOp(NoOpReason.NO_POSITION, null);
        }
        AccountTotals t = totals.get();
        long foreignBalanceMinor = t.debitMinor() - t.creditMinor();
        long carryingBaseMinor = t.baseDebitMinor() - t.baseCreditMinor();
        if (foreignBalanceMinor == 0L) {
            return Result.noOp(NoOpReason.NO_POSITION, null);
        }

        // (4b) Resolve the settled portion F_settle. null → full settlement (F,
        //      byte-identical to the 10th). A supplied partial must be non-zero, the
        //      SAME sign as F, and no larger in magnitude than |F| (over-settle would
        //      drive the residual negative / flip the position) → 422
        //      SETTLEMENT_AMOUNT_INVALID; nothing persists, the key is not consumed.
        long settleForeignMinor = resolveSettleForeignMinor(cmd.settleForeignMinor(), foreignBalanceMinor);

        // (4c) Resolve the effective settlement rate (24th increment — TASK-FIN-BE-032, ADR-002
        //      D3/D4). Resolved AFTER the no-op returns + the settle-amount validation, BEFORE the
        //      compute — so a no-op (no position) needs NO rate (AC-5; an omitted rate still 200s
        //      a no-op above). A supplied rate is returned verbatim (fromFeed=false → net-zero,
        //      byte-identical); an omitted rate falls back to the fresh cached quote, else 422
        //      FX_RATE_UNAVAILABLE (nothing persists, the key is not consumed — AC-3).
        ResolvedFxRate rate = fxRateResolver.resolve(
                cmd.tenantId(), LedgerReportingCurrency.BASE, cmd.currency(), cmd.settlementRate());

        // (5) Compute. settlementRate ≤ 0 → SETTLEMENT_RATE_INVALID (422). A position
        //     always books an entry (even realized == 0 — a 2-line removal+proceeds).
        //     F_settle == F → full settlement (residual exactly 0); F_settle < |F| →
        //     partial, residual (F − F_settle, C − C_settle) stays OPEN.
        //
        //     The carrying basis C_settle is chosen by the resolved cost-flow method
        //     (17th increment — TASK-FIN-BE-025, ADR-001 D3). Resolved AFTER all the
        //     guards above so FIFO and weighted-average share the identical guard surface.
        //
        //     Resolution precedence (21st increment — TASK-FIN-BE-029, ADR-001 D1 follow-up):
        //       account override (tenant, ledgerAccountCode) > tenant default (tenant) >
        //       WEIGHTED_AVERAGE.
        //     A per-account override lets an operator pin one ledger account to FIFO (or
        //     weighted-average) regardless of the tenant default — it can upgrade OR downgrade.
        //     When no account override row exists the result is identical to FIN-BE-028 (the
        //     tenant default, else WEIGHTED_AVERAGE) — net-zero.
        //       - WEIGHTED_AVERAGE / unset: the pre-existing weighted-average path —
        //         byte-identical, net-zero (FxSettlementPolicy.settle derives C_settle
        //         from the pool average).
        //       - FIFO: walk the open lots (acquired_at, seq) ASC, summing each lot's
        //         carrying slice into C_settle_fifo, then build the SAME entry shape via
        //         FxSettlementPolicy.settleWithCarrying(C_settle_fifo). Lots absent/short
        //         (Σremaining < |F_settle|) → SAFE FALLBACK to weighted-average (no
        //         net-non-zero), with NO lot mutation persisted.
        Optional<CostFlowMethod> accountOverride = fxCostFlowAccountConfigRepository
                .findByTenantIdAndAccountCode(cmd.tenantId(), cmd.ledgerAccountCode())
                .map(FxCostFlowAccountConfig::method);
        Optional<CostFlowMethod> tenantDefault = fxCostFlowConfigRepository
                .findByTenantId(cmd.tenantId())
                .map(FxCostFlowConfig::method);
        CostFlowMethod method = resolveCostFlowMethod(accountOverride, tenantDefault);
        Optional<SettlementResult> computed = (method == CostFlowMethod.FIFO)
                ? computeFifo(cmd, foreignBalanceMinor, carryingBaseMinor, settleForeignMinor,
                        rate.rate())
                : FxSettlementPolicy.settle(
                        cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency(),
                        foreignBalanceMinor, carryingBaseMinor, settleForeignMinor,
                        rate.rate(), cmd.proceedsAccountCode());
        if (computed.isEmpty()) {
            // F == 0 was already handled above; this is defensive.
            return Result.noOp(NoOpReason.NO_POSITION, null);
        }
        SettlementResult result = computed.get();

        // (6) Build the balanced settlement entry, record the dedupe row in the SAME Tx,
        //     then funnel through the guarded write path (closed-period guard → 422
        //     here; audit actor = operator subject; entry.posted outbox append with
        //     sourceType = SETTLEMENT).
        Instant postedAt = cmd.postedAt() != null ? cmd.postedAt() : clock.now();
        JournalEntry entry = JournalEntry.post(LedgerWriteSupport.newEntryId(), cmd.tenantId(), postedAt,
                SourceRef.ofSettlement(cmd.reference(), dedupeKey), result.lines());
        processedEventStore.markProcessed(dedupeKey, cmd.tenantId(), DEDUPE_TOPIC,
                entry.source().getSourceTransactionId(), clock.now());
        // Audit reason: byte-identical to the manual path — the rate source is appended whenever
        // the rate was NOT operator-supplied, i.e. it came from the market feed OR a per-tenant
        // contract override (28th increment — TASK-FIN-BE-042; AC-2 traceability: an operator can
        // see WHY a contract rate was used). Net-zero on the manual path ("manual" → no suffix).
        String reason = LedgerWriteSupport.auditReason(cmd.memo(), cmd.reference(), "FX settlement");
        String auditReason = "manual".equals(rate.sourceDescription())
                ? reason
                : reason + " [fx-rate " + rate.sourceDescription() + "]";
        JournalEntry posted = postJournalEntryUseCase.post(entry, auditReason, cmd.operatorSubject());

        // The residual OPEN position left after a partial settle — (F − F_settle,
        // C − C_settle). Exactly (0, 0) on a full settle (F_settle == F, C_settle == C).
        long residualForeignMinor = foreignBalanceMinor - result.settledForeignMinor();
        long residualCarryingBaseMinor = carryingBaseMinor - result.carryingSettledMinor();
        return Result.settled(result, residualForeignMinor, residualCarryingBaseMinor, posted);
    }

    /**
     * Resolve the settled foreign portion {@code F_settle} against the loaded position's
     * foreign balance {@code F}. {@code null} → full settlement ({@code F}). A supplied
     * partial is rejected (422 {@code SETTLEMENT_AMOUNT_INVALID}) when it is zero, has the
     * opposite sign to {@code F}, or exceeds {@code |F|} (over-settle, F1/F4). The
     * over-settle / sign guard lives <b>here</b> in the use case — {@code FxSettlementPolicy}
     * delegates and trusts the bounds.
     */
    private static long resolveSettleForeignMinor(Long settleForeignMinor, long foreignBalanceMinor) {
        if (settleForeignMinor == null) {
            return foreignBalanceMinor; // full settlement (net-zero, AC-2)
        }
        long fSettle = settleForeignMinor;
        if (fSettle == 0L) {
            throw new SettlementAmountInvalidException("settleForeignAmount must not be zero");
        }
        if (Long.signum(fSettle) != Long.signum(foreignBalanceMinor)) {
            throw new SettlementAmountInvalidException(
                    "settleForeignAmount must have the same sign as the position's foreign balance");
        }
        if (Math.abs(fSettle) > Math.abs(foreignBalanceMinor)) {
            throw new SettlementAmountInvalidException(
                    "settleForeignAmount exceeds the position's foreign balance (over-settle)");
        }
        return fSettle;
    }

    /**
     * Resolve the effective FX cost-flow method for a settlement (21st increment —
     * TASK-FIN-BE-029, ADR-001 D1 follow-up). Pure (no I/O — unit-testable without
     * Testcontainers): the per-account override wins when present, else the per-tenant default,
     * else {@link CostFlowMethod#WEIGHTED_AVERAGE}. The override can both upgrade (WEIGHTED_AVERAGE
     * tenant → FIFO account) and downgrade (FIFO tenant → WEIGHTED_AVERAGE account). When the
     * account override is absent the result is identical to the prior tenant-only resolution
     * (net-zero).
     *
     * @param accountOverride the per-account override method, if any (highest precedence)
     * @param tenantDefault   the per-tenant default method, if any (middle precedence)
     * @return the effective method (never {@code null})
     */
    static CostFlowMethod resolveCostFlowMethod(Optional<CostFlowMethod> accountOverride,
                                                Optional<CostFlowMethod> tenantDefault) {
        return accountOverride.or(() -> tenantDefault).orElse(CostFlowMethod.WEIGHTED_AVERAGE);
    }

    /**
     * Compute the settlement under the FIFO cost-flow method (17th increment —
     * TASK-FIN-BE-025, ADR-001 D3). Loads the open lots {@code (acquired_at, seq)} ASC and
     * walks them via {@link #walkFifo(List, long)} to derive the lot-exact settled carrying
     * {@code C_settle_fifo}. On success it persists the consumed lots' decremented
     * remaining/carrying in the SAME {@code @Transactional} (atomic with the entry) and
     * builds the entry via {@link FxSettlementPolicy#settleWithCarrying} (same shape as
     * weighted-average; only the carrying basis differs).
     *
     * <p><b>Safe fallback</b> — when the open lots are absent or short
     * ({@code Σremaining < |F_settle|}, e.g. a non-settlement reduction created a shadow
     * desync, or a pre-lot position), it does <b>not</b> mutate/persist any lot and falls
     * back to the weighted-average path (no net-non-zero), logging {@code FX_FIFO_LOT_SHORTFALL}.
     * The walk is computed first (no mutation), and lots are consumed + saved only after the
     * shortfall check passes — so a fallback leaves every lot untouched.
     */
    private Optional<SettlementResult> computeFifo(SettleForeignPositionCommand cmd,
                                                   long foreignBalanceMinor, long carryingBaseMinor,
                                                   long settleForeignMinor,
                                                   BigDecimal settlementRate) {
        List<FxPositionLot> openLots = fxPositionLotRepository.findOpenLots(
                cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency());
        FifoWalk walk = walkFifo(openLots, Math.abs(settleForeignMinor));
        if (walk == null) {
            // Σ open-lot remaining < |F_settle| — lots absent/short. Fall back to the
            // weighted-average carrying basis; persist NO lot mutation (net-non-zero avoided).
            log.warn("FX_FIFO_LOT_SHORTFALL tenant={} account={} currency={} settleForeign={} "
                            + "openLots={} — falling back to weighted-average carrying",
                    cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency(),
                    settleForeignMinor, openLots.size());
            return FxSettlementPolicy.settle(
                    cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency(),
                    foreignBalanceMinor, carryingBaseMinor, settleForeignMinor,
                    settlementRate, cmd.proceedsAccountCode());
        }

        // C_settle carries the same sign as F (the policy expects a signed C_settle); the
        // walk summed magnitudes, so re-apply sign(F).
        long carryingSettledFifo = foreignBalanceMinor < 0L
                ? -walk.carryingSettledMinor() : walk.carryingSettledMinor();

        // Persist the consumed lots' decrements in the SAME Tx (atomic with the entry).
        for (FxPositionLot consumed : walk.consumedLots()) {
            fxPositionLotRepository.save(consumed);
        }

        return FxSettlementPolicy.settleWithCarrying(
                cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency(),
                foreignBalanceMinor, settleForeignMinor, carryingSettledFifo,
                settlementRate, cmd.proceedsAccountCode());
    }

    /** The result of a FIFO walk: the summed settled carrying (magnitude) + the mutated lots. */
    record FifoWalk(long carryingSettledMinor, List<FxPositionLot> consumedLots) {
    }

    /**
     * Walk the open lots oldest-first, consuming {@code neededForeign} (a positive
     * magnitude {@code |F_settle|}) and summing each lot's carrying slice. The lots are
     * already ordered {@code (acquired_at, seq)} ASC by the repository. For each lot the
     * consumed quantity is {@code min(lot.remaining, needed)} and its carrying slice is
     * {@code round(lot.carrying × consumed / lot.remaining, HALF_UP)} — when a lot is fully
     * consumed ({@code consumed == lot.remaining}) the slice is exactly its whole carrying
     * (drift 0). It <b>mutates</b> the lot objects (via {@link FxPositionLot#consume}) and
     * collects them; the caller persists them only after this returns non-{@code null}.
     *
     * <p>Returns {@code null} when the open lots cannot cover {@code neededForeign}
     * ({@code Σremaining < neededForeign}) — the shortfall signal for the safe fallback. In
     * that case any in-memory mutations are discarded (the lot entities are managed but the
     * caller does NOT {@code save} them; they are not reloaded for the fallback path).
     */
    static FifoWalk walkFifo(List<FxPositionLot> openLots, long neededForeign) {
        long needed = neededForeign;
        long carryingSettled = 0L;
        List<FxPositionLot> consumedLots = new ArrayList<>();
        for (FxPositionLot lot : openLots) {
            if (needed == 0L) {
                break;
            }
            long remaining = lot.remainingForeignMinor();
            if (remaining <= 0L) {
                continue;
            }
            long consume = Math.min(remaining, needed);
            long slice = (consume == remaining)
                    ? lot.carryingBaseMinor() // fully consumed → exact whole carrying (no drift)
                    : new BigDecimal(lot.carryingBaseMinor())
                            .multiply(new BigDecimal(consume))
                            .divide(new BigDecimal(remaining), 0, RoundingMode.HALF_UP)
                            .longValueExact();
            lot.consume(consume, slice);
            consumedLots.add(lot);
            carryingSettled += slice;
            needed -= consume;
        }
        if (needed > 0L) {
            return null; // Σ open-lot remaining < neededForeign — shortfall
        }
        return new FifoWalk(carryingSettled, consumedLots);
    }
}
