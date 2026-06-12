package com.example.finance.ledger.application;

import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.NoOpReason;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.Result;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.RevaluationRateInvalidException;
import com.example.finance.ledger.domain.journal.FxRevaluationPolicy.Outcome;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
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
    @Mock ClockPort clock;

    RevalueForeignBalanceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RevalueForeignBalanceUseCase(
                journalRepository, processedEventStore, postJournalEntryUseCase, clock);
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

    private static List<JournalLine> revaluationLines() {
        Money baseDelta = Money.of(5_000L, Currency.KRW);
        return List.of(
                JournalLine.baseAdjustment(TENANT, CASH, Currency.USD,
                        com.example.finance.ledger.domain.journal.EntryDirection.DEBIT,
                        baseDelta, new BigDecimal("13.5")),
                JournalLine.credit(TENANT, LedgerAccountCodes.FX_GAIN, baseDelta));
    }
}
