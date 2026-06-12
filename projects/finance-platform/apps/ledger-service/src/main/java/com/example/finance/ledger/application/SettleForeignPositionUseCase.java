package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy.SettlementResult;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository.AccountTotals;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
    /** Max client key length — {@code "settle:" + key} (57) must fit the 64-char column. */
    static final int MAX_KEY_LENGTH = 50;

    private final JournalRepository journalRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final ProcessedEventStore processedEventStore;
    private final PostJournalEntryUseCase postJournalEntryUseCase;
    private final ClockPort clock;

    /** Why a settlement booked nothing ({@code settled=false}). */
    public enum NoOpReason {
        REPLAY, NO_POSITION
    }

    /**
     * The settlement outcome. {@code settled=true} → an entry was booked (the
     * controller maps it to 201) with the signed {@code realizedBaseMinor} + the
     * {@code proceedsBaseMinor} + the FX {@code outcome}; {@code settled=false} → a
     * no-op/replay (200) carrying the {@link NoOpReason} and the original entry on a
     * replay (else {@code null}).
     */
    public record Result(boolean settled, long realizedBaseMinor, long proceedsBaseMinor,
                         FxSettlementPolicy.Outcome outcome, NoOpReason reason,
                         JournalEntry entry) {

        static Result settled(SettlementResult r, JournalEntry entry) {
            return new Result(true, r.realized(), r.proceedsBase(), r.outcome(), null, entry);
        }

        static Result noOp(NoOpReason reason, JournalEntry entry) {
            return new Result(false, 0L, 0L, null, reason, entry);
        }
    }

    @Transactional
    public Result settle(SettleForeignPositionCommand cmd) {
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
                            "settlement entry for idempotency key not found (replay): " + dedupeKey));
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

        // (5) Compute. settlementRate ≤ 0 → SETTLEMENT_RATE_INVALID (422). A position
        //     always books an entry (even realized == 0 — a 2-line removal+proceeds).
        Optional<SettlementResult> computed = FxSettlementPolicy.settle(
                cmd.tenantId(), cmd.ledgerAccountCode(), cmd.currency(),
                foreignBalanceMinor, carryingBaseMinor, cmd.settlementRate(),
                cmd.proceedsAccountCode());
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
        JournalEntry entry = JournalEntry.post(newEntryId(), cmd.tenantId(), postedAt,
                SourceRef.ofSettlement(cmd.reference(), dedupeKey), result.lines());
        processedEventStore.markProcessed(dedupeKey, cmd.tenantId(), DEDUPE_TOPIC,
                entry.source().getSourceTransactionId(), clock.now());
        JournalEntry posted = postJournalEntryUseCase.post(entry, reason(cmd), cmd.operatorSubject());
        return Result.settled(result, posted);
    }

    private static String reason(SettleForeignPositionCommand cmd) {
        if (cmd.memo() != null && !cmd.memo().isBlank()) {
            return cmd.memo();
        }
        if (cmd.reference() != null && !cmd.reference().isBlank()) {
            return cmd.reference();
        }
        return "FX settlement";
    }

    private static String newEntryId() {
        return UUID.randomUUID().toString();
    }
}
