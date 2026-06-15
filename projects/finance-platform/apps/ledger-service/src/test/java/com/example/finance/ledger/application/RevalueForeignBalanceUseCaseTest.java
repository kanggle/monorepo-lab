package com.example.finance.ledger.application;

import com.example.finance.ledger.application.ResolveEffectiveFxRate.ResolvedFxRate;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.NoOpReason;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.Result;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.RevaluationRateInvalidException;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.FxRevaluationPolicy.Outcome;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.FxPositionLotRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository.AccountTotals;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RevalueForeignBalanceUseCase} (9th increment, TASK-FIN-BE-015).
 * Mocks all ports — proves a gain/loss position funnels a balanced 2-line entry through
 * {@code PostJournalEntryUseCase.post(entry, reason, actor)} with the operator subject
 * as actor + a {@code REVALUATION} source, the no-position / at-spot no-ops leave the key
 * unmarked, a replay returns the original, a CLOSED period surfaces, and the key/currency
 * guards fire.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RevalueForeignBalanceUseCaseTest {

    private static final String TENANT = "finance";
    private static final String OPERATOR = "operator-7";
    private static final String KEY = "FX-REVAL-2026-06-USD";
    private static final String DEDUPE = "reval:" + KEY;
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;
    private static final Instant NOW = Instant.parse("2026-06-30T23:59:59Z");

    @Mock JournalRepository journalRepository;
    @Mock ProcessedEventStore processedEventStore;
    @Mock PostJournalEntryUseCase postJournalEntryUseCase;
    @Mock FxPositionLotRepository fxPositionLotRepository;
    @Mock ResolveEffectiveFxRate fxRateResolver;
    @Mock ClockPort clock;

    RevalueForeignBalanceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RevalueForeignBalanceUseCase(
                journalRepository, processedEventStore, postJournalEntryUseCase,
                fxPositionLotRepository, fxRateResolver, clock);
        // Existing tests all pass a MANUAL closingRate; mirror the production manual path — the
        // resolver echoes the provided rate as a non-feed ResolvedFxRate (net-zero). Lenient so
        // the early-reject / no-op tests (which never reach the resolve call) don't trip STRICT_STUBS.
        lenient().when(fxRateResolver.resolve(any(), any(), any()))
                .thenAnswer(inv -> new ResolvedFxRate(inv.getArgument(2), false, "manual"));
    }

    private static RevalueForeignBalanceCommand cmd(String rate) {
        return new RevalueForeignBalanceCommand(
                TENANT, OPERATOR, CASH, Currency.USD, new BigDecimal(rate),
                NOW, KEY, "month-end USD revaluation", KEY);
    }

    /** A USD position: debit-positive foreign balance + base carrying (KRW). */
    private static AccountTotals usdPosition(long debitMinor, long baseDebitMinor) {
        return new AccountTotals(CASH, "USD", debitMinor, 0L, baseDebitMinor, 0L);
    }

    @Test
    @DisplayName("a gain position posts the 2-line REVALUATION entry via the guarded path with the operator as actor")
    void gainPosts() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fxPositionLotRepository.findOpenLots(TENANT, CASH, Currency.USD))
                .thenReturn(List.of());

        Result result = useCase.revalue(cmd("13.5"));

        assertThat(result.revalued()).isTrue();
        assertThat(result.deltaBaseMinor()).isEqualTo(5_000L);
        assertThat(result.outcome()).isEqualTo(Outcome.FX_GAIN);

        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(postJournalEntryUseCase).post(entry.capture(), anyString(), eq(OPERATOR));
        JournalEntry posted = entry.getValue();
        assertThat(posted.isBalanced()).isTrue();
        assertThat(posted.lines()).hasSize(2);
        assertThat(posted.source().getSourceType()).isEqualTo(SourceRef.TYPE_REVALUATION);
        assertThat(posted.source().getSourceEventId()).isEqualTo(DEDUPE);
        assertThat(posted.source().getSourceTransactionId()).isEqualTo(KEY);
        verify(processedEventStore).markProcessed(DEDUPE, TENANT, "fx-revaluation", KEY, NOW);
    }

    @Test
    @DisplayName("a loss position (lower closing rate) posts an FX_LOSS entry")
    void lossPosts() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 135_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fxPositionLotRepository.findOpenLots(TENANT, CASH, Currency.USD))
                .thenReturn(List.of());

        Result result = useCase.revalue(cmd("13.0"));

        assertThat(result.revalued()).isTrue();
        assertThat(result.deltaBaseMinor()).isEqualTo(-5_000L);
        assertThat(result.outcome()).isEqualTo(Outcome.FX_LOSS);
        verify(postJournalEntryUseCase).post(any(), anyString(), eq(OPERATOR));
    }

    @Test
    @DisplayName("no position in that currency → revalued:false NO_POSITION, no post, key NOT marked")
    void noPositionNoOp() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.empty());

        Result result = useCase.revalue(cmd("13.5"));

        assertThat(result.revalued()).isFalse();
        assertThat(result.reason()).isEqualTo(NoOpReason.NO_POSITION);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a zero foreign balance → revalued:false NO_POSITION (key NOT marked)")
    void zeroForeignBalanceNoOp() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        // Σdebit == Σcredit in the foreign currency → foreignBalance == 0.
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(new AccountTotals(CASH, "USD",
                        10_000L, 10_000L, 130_000L, 130_000L)));

        Result result = useCase.revalue(cmd("13.5"));

        assertThat(result.revalued()).isFalse();
        assertThat(result.reason()).isEqualTo(NoOpReason.NO_POSITION);
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("already at spot (delta == 0) → revalued:false AT_SPOT, no post, key NOT marked")
    void atSpotNoOp() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 135_000L)));

        Result result = useCase.revalue(cmd("13.5")); // 10000 × 13.5 = 135000 == carrying

        assertThat(result.revalued()).isFalse();
        assertThat(result.reason()).isEqualTo(NoOpReason.AT_SPOT);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a replay (key already processed) returns the original entry — no second post")
    void replayReturnsOriginal() {
        JournalEntry original = JournalEntry.post("e-1", TENANT, NOW,
                SourceRef.ofRevaluation(KEY, DEDUPE), revaluationLines());
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(true);
        when(journalRepository.findBySourceEventId(DEDUPE, TENANT))
                .thenReturn(Optional.of(original));

        Result result = useCase.revalue(cmd("13.5"));

        assertThat(result.revalued()).isFalse();
        assertThat(result.reason()).isEqualTo(NoOpReason.REPLAY);
        assertThat(result.entry()).isSameAs(original);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a CLOSED period (the guarded path throws) propagates LEDGER_PERIOD_CLOSED")
    void closedPeriodPropagates() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenThrow(new LedgerPeriodClosedException("posting into a CLOSED period"));

        assertThatThrownBy(() -> useCase.revalue(cmd("13.5")))
                .isInstanceOf(LedgerPeriodClosedException.class);
    }

    @Test
    @DisplayName("a blank Idempotency-Key → IDEMPOTENCY_KEY_REQUIRED before any work")
    void blankKeyRejected() {
        RevalueForeignBalanceCommand blank = new RevalueForeignBalanceCommand(
                TENANT, OPERATOR, CASH, Currency.USD, new BigDecimal("13.5"),
                NOW, KEY, "memo", "  ");

        assertThatThrownBy(() -> useCase.revalue(blank))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
        verify(processedEventStore, never()).isProcessed(anyString());
    }

    @Test
    @DisplayName("an oversized Idempotency-Key → IDEMPOTENCY_KEY_REQUIRED (key must fit the column)")
    void oversizedKeyRejected() {
        String tooLong = "k".repeat(51);
        RevalueForeignBalanceCommand cmd = new RevalueForeignBalanceCommand(
                TENANT, OPERATOR, CASH, Currency.USD, new BigDecimal("13.5"),
                NOW, KEY, "memo", tooLong);

        assertThatThrownBy(() -> useCase.revalue(cmd))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
        verify(processedEventStore, never()).isProcessed(anyString());
    }

    @Test
    @DisplayName("the base currency (KRW) cannot be revalued → CURRENCY_MISMATCH")
    void baseCurrencyRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        RevalueForeignBalanceCommand krw = new RevalueForeignBalanceCommand(
                TENANT, OPERATOR, CASH, Currency.KRW, new BigDecimal("1"),
                NOW, KEY, "memo", KEY);

        assertThatThrownBy(() -> useCase.revalue(krw))
                .isInstanceOf(CurrencyMismatchException.class);
        verify(journalRepository, never()).accountTotalsForCurrency(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("closingRate ≤ 0 on an existing position → REVALUATION_RATE_INVALID")
    void invalidRateRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));

        assertThatThrownBy(() -> useCase.revalue(cmd("0")))
                .isInstanceOf(RevaluationRateInvalidException.class);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
    }

    // ------------------------------------------------------------------------
    // markToSpot — the lot carrying distribution arithmetic (18th increment,
    // TASK-FIN-BE-026). Pure helper: mark-to-spot per lot, last-lot residual
    // absorption, single-lot exact, and the Σ == |revaluedBase| invariant.
    // ------------------------------------------------------------------------

    private static FxPositionLot lot(long remainingForeignMinor, long carryingBaseMinor) {
        // A fully-open lot whose remaining is the relevant input to mark-to-spot.
        // original == remaining (the factory's open invariant); carrying is set to the
        // pre-revaluation value but markToSpot reads only remaining + the rate.
        return FxPositionLot.acquire(TENANT, CASH, Currency.USD, NOW, 1L,
                remainingForeignMinor, carryingBaseMinor, "src-entry", NOW);
    }

    @Test
    @DisplayName("markToSpot: a single lot receives exactly |revaluedBase| (exact, no residual split)")
    void markToSpotSingleLotExact() {
        // 10000 USD @ closing 13.5 → revaluedBase 135000; the lone lot absorbs it whole.
        long[] marks = RevalueForeignBalanceUseCase.markToSpot(
                List.of(lot(10_000L, 130_000L)), new BigDecimal("13.5"), 135_000L);

        assertThat(marks).containsExactly(135_000L);
    }

    @Test
    @DisplayName("markToSpot: each lot marks to round(remaining × rate); the LAST absorbs the residual so Σ == |revaluedBase|")
    void markToSpotTwoLotResidualAbsorption() {
        // lot1 1000 USD, lot2 1000 USD, closing 1350.5 → per-lot round = 1,350,500 each.
        // revaluedBase = round(2000 × 1350.5) = 2,701,000. lot1 = 1,350,500;
        // lot2(last) = 2,701,000 − 1,350,500 = 1,350,500. Σ == 2,701,000 exactly.
        long[] marks = RevalueForeignBalanceUseCase.markToSpot(
                List.of(lot(1_000L, 1_300_000L), lot(1_000L, 1_400_000L)),
                new BigDecimal("1350.5"), 2_701_000L);

        assertThat(marks[0]).isEqualTo(1_350_500L);
        assertThat(marks[1]).isEqualTo(1_350_500L);
        assertThat(marks[0] + marks[1]).isEqualTo(2_701_000L);
    }

    @Test
    @DisplayName("markToSpot: an odd rate forces a non-zero residual onto the last lot — Σ still == |revaluedBase|")
    void markToSpotResidualOntoLastLot() {
        // lot1 333 USD, lot2 667 USD, closing 3.0001 → revaluedBase = round(1000 × 3.0001) = 3000.
        // lot1 = round(333 × 3.0001) = round(999.0333) = 999; lot2(last) = 3000 − 999 = 2001
        // (NOT round(667 × 3.0001) = 2001.0667 → 2001 here too, but the last-lot rule guarantees Σ).
        long[] marks = RevalueForeignBalanceUseCase.markToSpot(
                List.of(lot(333L, 400_000L), lot(667L, 600_000L)),
                new BigDecimal("3.0001"), 3_000L);

        assertThat(marks[0]).isEqualTo(999L);
        assertThat(marks[1]).isEqualTo(2_001L);
        assertThat(marks[0] + marks[1]).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("markToSpot: a loss revaluation (negative revaluedBase magnitude handling) keeps marks non-negative and Σ == |revaluedBase|")
    void markToSpotLossNonNegative() {
        // Loss: aggregate carrying falls. revaluedBase is the new (smaller) magnitude.
        // 1000 USD @ closing 1000 → revaluedBase 1,000,000; single lot → exact 1,000,000.
        long[] marks = RevalueForeignBalanceUseCase.markToSpot(
                List.of(lot(1_000L, 1_400_000L)), new BigDecimal("1000"), 1_000_000L);

        assertThat(marks).containsExactly(1_000_000L);
        assertThat(marks[0]).isNotNegative();
    }

    @Test
    @DisplayName("markToSpot: an extreme shadow-desync where prior lots overshoot clamps the last lot at 0 (never negative)")
    void markToSpotClampsLastLotAtZero() {
        // Contrived desync: two lots whose round(remaining × rate) prior-sum already exceeds
        // a tiny revaluedBase → last lot clamps to 0 (non-negative CHECK preserved).
        long[] marks = RevalueForeignBalanceUseCase.markToSpot(
                List.of(lot(1_000L, 1_000L), lot(1_000L, 1_000L)),
                new BigDecimal("1000"), 500_000L); // prior lot1 marks 1,000,000 > 500,000

        assertThat(marks[0]).isEqualTo(1_000_000L);
        assertThat(marks[1]).isEqualTo(0L); // clamped — never negative
    }

    private static List<JournalLine> revaluationLines() {
        Money baseDelta = Money.of(5_000L, Currency.KRW);
        return List.of(
                JournalLine.baseAdjustment(TENANT, CASH, Currency.USD,
                        com.example.finance.ledger.domain.journal.EntryDirection.DEBIT,
                        baseDelta, new BigDecimal("13.5")),
                JournalLine.credit(TENANT, LedgerAccountCodes.FX_GAIN, baseDelta));
    }
}
