package com.example.finance.ledger.application;

import com.example.finance.ledger.application.ResolveEffectiveFxRate.ResolvedFxRate;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.FxRevaluationPolicy;
import com.example.finance.ledger.domain.journal.FxRevaluationPolicy.RevaluationResult;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.SourceRef;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FX gain/loss revaluation use case (9th increment, TASK-FIN-BE-015 — architecture.md
 * § FX gain/loss revaluation). An operator revalues a {@code (ledgerAccountCode,
 * currency)} foreign position at a closing (spot) rate, truing its base carrying value
 * to spot and booking the delta to {@code FX_GAIN}/{@code FX_LOSS}. Like manual posting
 * (5th increment) it adds <b>no new write boundary</b> — it builds a balanced
 * base-currency (KRW) adjusting entry via {@link FxRevaluationPolicy} and funnels it
 * through the existing {@link PostJournalEntryUseCase#post(JournalEntry, String, String)}
 * (the single guarded write path), inheriting the closed-period guard, the audit row
 * (actor = the operator subject), and the {@code entry.posted} outbox append.
 *
 * <p>Idempotent (F1) on a client {@code Idempotency-Key} namespaced {@code reval:{key}}.
 * A replay returns the original entry (no re-post). A no-op (no position / already at
 * spot) returns {@code revalued=false} and does <b>not</b> consume the key (net-zero —
 * a real position can be revalued later).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevalueForeignBalanceUseCase {

    static final String DEDUPE_PREFIX = "reval:";
    static final String DEDUPE_TOPIC = "fx-revaluation";
    /** Max client key length — {@code "reval:" + key} (56) must fit the 64-char column. */
    static final int MAX_KEY_LENGTH = 50;

    private final JournalRepository journalRepository;
    private final ProcessedEventStore processedEventStore;
    private final PostJournalEntryUseCase postJournalEntryUseCase;
    private final FxPositionLotRepository fxPositionLotRepository;
    private final ResolveEffectiveFxRate fxRateResolver;
    private final ClockPort clock;

    /** Why a revaluation booked nothing ({@code revalued=false}). */
    public enum NoOpReason {
        REPLAY, NO_POSITION, AT_SPOT
    }

    /**
     * The revaluation outcome. {@code revalued=true} → an entry was booked (the
     * controller maps it to 201) with the signed {@code deltaBaseMinor} + the FX
     * {@code outcome}; {@code revalued=false} → a no-op/replay (200) carrying the
     * {@link NoOpReason} and the original entry on a replay (else {@code null}).
     */
    public record Result(boolean revalued, long deltaBaseMinor,
                         FxRevaluationPolicy.Outcome outcome, NoOpReason reason,
                         JournalEntry entry) {

        static Result revalued(RevaluationResult r, JournalEntry entry) {
            return new Result(true, r.delta(), r.outcome(), null, entry);
        }

        static Result noOp(NoOpReason reason, JournalEntry entry) {
            return new Result(false, 0L, null, reason, entry);
        }
    }

    @Transactional
    public Result revalue(RevalueForeignBalanceCommand cmd) {
        String key = cmd.idempotencyKey();
        if (key == null || key.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        String dedupeKey = DEDUPE_PREFIX + key;

        // (1) Idempotent replay — the key was already processed; return the original
        //     entry (no re-post). The unique constraint on processed_events makes a
        //     concurrent double-submit race-safe (the loser lands here).
        if (processedEventStore.isProcessed(dedupeKey)) {
            JournalEntry original = journalRepository
                    .findBySourceEventId(dedupeKey, cmd.tenantId())
                    .orElseThrow(() -> new JournalEntryNotFoundException(
                            "revaluation entry for idempotency key not found (replay): " + dedupeKey));
            return Result.noOp(NoOpReason.REPLAY, original);
        }

        // (2) The base currency cannot be revalued against itself (CURRENCY_MISMATCH).
        if (cmd.currency() == LedgerReportingCurrency.BASE) {
            throw new CurrencyMismatchException(
                    "the base currency (" + LedgerReportingCurrency.BASE
                            + ") cannot be revalued against itself");
        }

        // (3) Load the position. No row / zero foreign balance → 200 revalued:false
        //     (net-zero; the key is NOT marked — a real position can be revalued later).
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

        // (3b) Resolve the effective closing rate (24th increment — TASK-FIN-BE-032, ADR-002
        //      D3/D4). Resolved AFTER the NO_POSITION no-op returns (an omitted rate still 200s a
        //      no-op above — AC-5; the AT_SPOT no-op below DOES need the rate, so resolve precedes
        //      it). A supplied rate is returned verbatim (fromFeed=false → net-zero); an omitted
        //      rate falls back to the fresh cached quote, else 422 FX_RATE_UNAVAILABLE (nothing
        //      persists, the key is not consumed — AC-3).
        ResolvedFxRate rate = fxRateResolver.resolve(
                LedgerReportingCurrency.BASE, cmd.currency(), cmd.closingRate());

        // (4) Compute. closingRate ≤ 0 → REVALUATION_RATE_INVALID (422). delta == 0
        //     (already at spot) → 200 revalued:false (key NOT marked).
        Optional<RevaluationResult> computed = FxRevaluationPolicy.revalue(
                cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency(),
                foreignBalanceMinor, carryingBaseMinor, rate.rate());
        if (computed.isEmpty()) {
            return Result.noOp(NoOpReason.AT_SPOT, null);
        }
        RevaluationResult result = computed.get();

        // (5) Build the balanced adjusting entry, record the dedupe row in the SAME Tx,
        //     then funnel through the guarded write path (closed-period guard → 422
        //     here; audit actor = operator subject; entry.posted outbox append with
        //     sourceType = REVALUATION).
        Instant postedAt = cmd.postedAt() != null ? cmd.postedAt() : clock.now();
        JournalEntry entry = JournalEntry.post(newEntryId(), cmd.tenantId(), postedAt,
                SourceRef.ofRevaluation(cmd.reference(), dedupeKey), result.lines());
        processedEventStore.markProcessed(dedupeKey, cmd.tenantId(), DEDUPE_TOPIC,
                entry.source().getSourceTransactionId(), clock.now());
        // Audit reason: byte-identical to the manual path (fromFeed=false) — the feed source is
        // appended ONLY when the rate came from the cache (AC-2 traceability; net-zero on manual).
        String auditReason = rate.fromFeed()
                ? reason(cmd) + " [fx-rate " + rate.sourceDescription() + "]"
                : reason(cmd);
        JournalEntry posted = postJournalEntryUseCase.post(entry, auditReason, cmd.operatorSubject());

        // (6) Lot carrying distribution (18th increment — TASK-FIN-BE-026, ADR-001 D4-a).
        //     Revaluation has just trued the AGGREGATE position carrying to spot
        //     (revaluedBase = carryingBaseMinor + delta). To preserve the D4 invariant
        //     "Σ open-lot carrying == aggregate carrying" — so FIN-BE-025 FIFO settlement
        //     stays lot-exact even on a revaluation-touched position — re-mark each open
        //     lot's carrying to its own foreign-at-spot value (the LAST lot absorbing the
        //     rounding residual so Σ == |revaluedBase| exactly). Applied in the SAME Tx,
        //     on the success path only (the no-op REPLAY/NO_POSITION/AT_SPOT returns above
        //     never reach here). Always-apply / net-zero: lots are universally consistent
        //     regardless of cost-flow config; weighted-average settlement reads the
        //     aggregate carrying (NOT the lots), so re-marking lots leaves non-FIFO
        //     settlement results byte-unchanged.
        long revaluedBase = carryingBaseMinor + result.delta();
        distributeRevaluationToLots(cmd, rate.rate(), revaluedBase);

        return Result.revalued(result, posted);
    }

    /**
     * Mark each open lot's carrying to its foreign-at-spot value, with the LAST lot
     * absorbing the rounding residual so {@code Σ open-lot carrying == |revaluedBase|}
     * (= the new aggregate carrying magnitude). Loads the open lots {@code (acquired_at,
     * seq)} ASC; an empty set → skip (the aggregate revaluation already posted;
     * weighted-average settlement is unaffected). For every lot but the last,
     * {@code newCarrying = round(remaining × closingRate, HALF_UP)} (magnitude); the last
     * lot takes {@code |revaluedBase| − Σ(prior)} to force the invariant exactly. Each
     * marked carrying is non-negative (closingRate > 0, remaining >= 0); the last-lot
     * residual is clamped at 0 for an extreme shadow-desync (documented edge — a normal
     * position never hits it). Persists in the caller's {@code @Transactional}.
     */
    private void distributeRevaluationToLots(RevalueForeignBalanceCommand cmd,
                                             BigDecimal closingRate, long revaluedBase) {
        List<FxPositionLot> openLots = fxPositionLotRepository.findOpenLots(
                cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency());
        if (openLots.isEmpty()) {
            return; // no lots → aggregate revaluation already posted; net-zero on settlement
        }
        long[] marks = markToSpot(openLots, closingRate, revaluedBase);
        for (int i = 0; i < openLots.size(); i++) {
            FxPositionLot lot = openLots.get(i);
            lot.markCarrying(marks[i]);
            fxPositionLotRepository.save(lot);
        }
    }

    /**
     * Pure mark-to-spot arithmetic (extracted for unit coverage). Returns the new
     * carrying magnitude for each open lot, FIFO-ordered, such that {@code Σ == |
     * revaluedBase|}. For every lot but the last,
     * {@code round(remaining × closingRate, HALF_UP)}; the last lot absorbs the residual
     * ({@code |revaluedBase| − Σ(prior)}), clamped at {@code 0} (extreme shadow-desync
     * edge). A single lot therefore receives exactly {@code |revaluedBase|}. Magnitudes
     * throughout (FIN-BE-024/025 store positive lot magnitudes).
     */
    static long[] markToSpot(List<FxPositionLot> openLots, BigDecimal closingRate, long revaluedBase) {
        long target = Math.abs(revaluedBase);
        int n = openLots.size();
        long[] marks = new long[n];
        long running = 0L;
        for (int i = 0; i < n; i++) {
            if (i < n - 1) {
                long newCarrying = new BigDecimal(openLots.get(i).remainingForeignMinor())
                        .multiply(closingRate)
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();
                marks[i] = newCarrying;
                running += newCarrying;
            } else {
                // LAST lot absorbs the residual so Σ == target exactly. Clamp at 0 for an
                // extreme desync where the prior lots already overshoot target.
                marks[i] = Math.max(0L, target - running);
            }
        }
        return marks;
    }

    private static String reason(RevalueForeignBalanceCommand cmd) {
        if (cmd.memo() != null && !cmd.memo().isBlank()) {
            return cmd.memo();
        }
        if (cmd.reference() != null && !cmd.reference().isBlank()) {
            return cmd.reference();
        }
        return "FX revaluation";
    }

    private static String newEntryId() {
        return UUID.randomUUID().toString();
    }
}
