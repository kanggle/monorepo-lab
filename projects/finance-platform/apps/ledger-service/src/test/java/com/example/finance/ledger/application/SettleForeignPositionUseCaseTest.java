package com.example.finance.ledger.application;

import com.example.finance.ledger.application.ResolveEffectiveFxRate.ResolvedFxRate;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.NoOpReason;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.Result;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.SettlementAmountInvalidException;
import com.example.finance.ledger.domain.error.LedgerErrors.SettlementRateInvalidException;
import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfig;
import com.example.finance.ledger.domain.journal.FxCostFlowConfig;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy.Outcome;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowAccountConfigRepository;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowConfigRepository;
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
 * Unit test for {@link SettleForeignPositionUseCase} (10th increment, TASK-FIN-BE-016).
 * Mocks all ports — proves a gain/loss position funnels a balanced 3-line entry through
 * {@code PostJournalEntryUseCase.post(entry, reason, actor)} with the operator subject
 * as actor + a {@code SETTLEMENT} source, the no-position no-op leaves the key unmarked,
 * a replay returns the original, an unknown proceeds account → 404, a CLOSED period
 * surfaces, and the key/currency/rate guards fire.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SettleForeignPositionUseCaseTest {

    private static final String TENANT = "finance";
    private static final String OPERATOR = "operator-7";
    private static final String KEY = "FX-SETTLE-2026-06-USD";
    private static final String DEDUPE = "settle:" + KEY;
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;
    private static final String PROCEEDS = LedgerAccountCodes.SETTLEMENT_SUSPENSE;
    private static final Instant NOW = Instant.parse("2026-06-30T23:59:59Z");

    @Mock JournalRepository journalRepository;
    @Mock LedgerAccountRepository ledgerAccountRepository;
    @Mock ProcessedEventStore processedEventStore;
    @Mock PostJournalEntryUseCase postJournalEntryUseCase;
    @Mock FxCostFlowConfigRepository fxCostFlowConfigRepository;
    @Mock FxCostFlowAccountConfigRepository fxCostFlowAccountConfigRepository;
    @Mock FxPositionLotRepository fxPositionLotRepository;
    @Mock ResolveEffectiveFxRate fxRateResolver;
    @Mock ClockPort clock;

    SettleForeignPositionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SettleForeignPositionUseCase(journalRepository, ledgerAccountRepository,
                processedEventStore, postJournalEntryUseCase, fxCostFlowConfigRepository,
                fxCostFlowAccountConfigRepository, fxPositionLotRepository, fxRateResolver, clock);
        // Existing tests all pass a MANUAL settlementRate; mirror the production manual path —
        // the resolver echoes the provided rate as a non-feed ResolvedFxRate (net-zero). Lenient
        // so the early-reject tests (which never reach the resolve call) don't trip STRICT_STUBS.
        lenient().when(fxRateResolver.resolve(any(), any(), any(), any()))
                .thenAnswer(inv -> new ResolvedFxRate(inv.getArgument(3), false, "manual"));
        // The cost-flow config is resolved after the guards on every settle that reaches the
        // policy call; absence (account override + tenant default) → WEIGHTED_AVERAGE (the
        // existing tests all assert the weighted-average path). Lenient so the early-reject tests
        // (which never reach the branch) don't trip STRICT_STUBS.
        lenient().when(fxCostFlowAccountConfigRepository.findByTenantIdAndAccountCode(TENANT, CASH))
                .thenReturn(Optional.empty());
        lenient().when(fxCostFlowConfigRepository.findByTenantId(TENANT))
                .thenReturn(Optional.empty());
    }

    private static SettleForeignPositionCommand cmd(String rate) {
        return new SettleForeignPositionCommand(
                TENANT, OPERATOR, CASH, Currency.USD, new BigDecimal(rate), PROCEEDS,
                null, NOW, KEY, "liquidate USD holdings", KEY);
    }

    private static SettleForeignPositionCommand partialCmd(String rate, Long settleForeignMinor) {
        return new SettleForeignPositionCommand(
                TENANT, OPERATOR, CASH, Currency.USD, new BigDecimal(rate), PROCEEDS,
                settleForeignMinor, NOW, KEY, "partial liquidation", KEY);
    }

    /** A USD asset position: debit-positive foreign balance + base carrying (KRW). */
    private static AccountTotals usdPosition(long debitMinor, long baseDebitMinor) {
        return new AccountTotals(CASH, "USD", debitMinor, 0L, baseDebitMinor, 0L);
    }

    @Test
    @DisplayName("a gain settlement posts the 3-line SETTLEMENT entry via the guarded path with the operator as actor")
    void gainPosts() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.settle(cmd("13.7"));

        assertThat(result.settled()).isTrue();
        assertThat(result.realizedBaseMinor()).isEqualTo(7_000L);
        assertThat(result.proceedsBaseMinor()).isEqualTo(137_000L);
        assertThat(result.outcome()).isEqualTo(Outcome.FX_GAIN);

        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(postJournalEntryUseCase).post(entry.capture(), anyString(), eq(OPERATOR));
        JournalEntry posted = entry.getValue();
        assertThat(posted.isBalanced()).isTrue();
        assertThat(posted.lines()).hasSize(3);
        assertThat(posted.source().getSourceType()).isEqualTo(SourceRef.TYPE_SETTLEMENT);
        assertThat(posted.source().getSourceEventId()).isEqualTo(DEDUPE);
        assertThat(posted.source().getSourceTransactionId()).isEqualTo(KEY);
        verify(processedEventStore).markProcessed(DEDUPE, TENANT, "fx-settlement", KEY, NOW);
    }

    @Test
    @DisplayName("a loss settlement (rate below carrying) posts an FX_LOSS entry")
    void lossPosts() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.settle(cmd("12.5"));

        assertThat(result.settled()).isTrue();
        assertThat(result.realizedBaseMinor()).isEqualTo(-5_000L);
        assertThat(result.proceedsBaseMinor()).isEqualTo(125_000L);
        assertThat(result.outcome()).isEqualTo(Outcome.FX_LOSS);
        verify(postJournalEntryUseCase).post(any(), anyString(), eq(OPERATOR));
    }

    @Test
    @DisplayName("no position in that currency → settled:false NO_POSITION, no post, key NOT marked")
    void noPositionNoOp() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.empty());

        Result result = useCase.settle(cmd("13.7"));

        assertThat(result.settled()).isFalse();
        assertThat(result.reason()).isEqualTo(NoOpReason.NO_POSITION);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a zero foreign balance → settled:false NO_POSITION (key NOT marked)")
    void zeroForeignBalanceNoOp() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        // Σdebit == Σcredit in the foreign currency → foreignBalance == 0.
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(new AccountTotals(CASH, "USD",
                        10_000L, 10_000L, 130_000L, 130_000L)));

        Result result = useCase.settle(cmd("13.7"));

        assertThat(result.settled()).isFalse();
        assertThat(result.reason()).isEqualTo(NoOpReason.NO_POSITION);
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("settling at carrying rate (realized == 0) still books a 2-line entry")
    void atCarryingStillBooks() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.settle(cmd("13.0")); // proceeds 130000 == carrying → realized 0

        assertThat(result.settled()).isTrue();
        assertThat(result.realizedBaseMinor()).isZero();
        assertThat(result.outcome()).isEqualTo(Outcome.NONE);
        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(postJournalEntryUseCase).post(entry.capture(), anyString(), eq(OPERATOR));
        assertThat(entry.getValue().lines()).hasSize(2);
        verify(processedEventStore).markProcessed(DEDUPE, TENANT, "fx-settlement", KEY, NOW);
    }

    @Test
    @DisplayName("a replay (key already processed) returns the original entry — no second post")
    void replayReturnsOriginal() {
        JournalEntry original = JournalEntry.post("e-1", TENANT, NOW,
                SourceRef.ofSettlement(KEY, DEDUPE), settlementLines());
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(true);
        when(journalRepository.findBySourceEventId(DEDUPE, TENANT))
                .thenReturn(Optional.of(original));

        Result result = useCase.settle(cmd("13.7"));

        assertThat(result.settled()).isFalse();
        assertThat(result.reason()).isEqualTo(NoOpReason.REPLAY);
        assertThat(result.entry()).isSameAs(original);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("an unknown proceeds account → LEDGER_ACCOUNT_NOT_FOUND, no post, key NOT marked")
    void unknownProceedsAccount() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(false);

        assertThatThrownBy(() -> useCase.settle(cmd("13.7")))
                .isInstanceOf(LedgerAccountNotFoundException.class);
        verify(journalRepository, never()).accountTotalsForCurrency(anyString(), any(), anyString());
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a CLOSED period (the guarded path throws) propagates LEDGER_PERIOD_CLOSED")
    void closedPeriodPropagates() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenThrow(new LedgerPeriodClosedException("posting into a CLOSED period"));

        assertThatThrownBy(() -> useCase.settle(cmd("13.7")))
                .isInstanceOf(LedgerPeriodClosedException.class);
    }

    @Test
    @DisplayName("a blank Idempotency-Key → IDEMPOTENCY_KEY_REQUIRED before any work")
    void blankKeyRejected() {
        SettleForeignPositionCommand blank = new SettleForeignPositionCommand(
                TENANT, OPERATOR, CASH, Currency.USD, new BigDecimal("13.7"), PROCEEDS,
                null, NOW, KEY, "memo", "  ");

        assertThatThrownBy(() -> useCase.settle(blank))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
        verify(processedEventStore, never()).isProcessed(anyString());
    }

    @Test
    @DisplayName("an oversized Idempotency-Key → IDEMPOTENCY_KEY_REQUIRED (key must fit the column)")
    void oversizedKeyRejected() {
        String tooLong = "k".repeat(51);
        SettleForeignPositionCommand cmd = new SettleForeignPositionCommand(
                TENANT, OPERATOR, CASH, Currency.USD, new BigDecimal("13.7"), PROCEEDS,
                null, NOW, KEY, "memo", tooLong);

        assertThatThrownBy(() -> useCase.settle(cmd))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
        verify(processedEventStore, never()).isProcessed(anyString());
    }

    @Test
    @DisplayName("the base currency (KRW) cannot be settled → CURRENCY_MISMATCH")
    void baseCurrencyRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        SettleForeignPositionCommand krw = new SettleForeignPositionCommand(
                TENANT, OPERATOR, CASH, Currency.KRW, new BigDecimal("1"), PROCEEDS,
                null, NOW, KEY, "memo", KEY);

        assertThatThrownBy(() -> useCase.settle(krw))
                .isInstanceOf(CurrencyMismatchException.class);
        verify(ledgerAccountRepository, never()).existsByCode(anyString(), anyString());
        verify(journalRepository, never()).accountTotalsForCurrency(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("settlementRate ≤ 0 on an existing position → SETTLEMENT_RATE_INVALID")
    void invalidRateRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));

        assertThatThrownBy(() -> useCase.settle(cmd("0")))
                .isInstanceOf(SettlementRateInvalidException.class);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
    }

    // ---- Partial / weighted-average settlement (12th increment — TASK-FIN-BE-018) ----

    @Test
    @DisplayName("a partial settle books C_settle and exposes the residual OPEN position")
    void partialSettleExposesResidual() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Settle $40 of the $100 position @ 13.7: C_settle = 52000, proceeds = 54800,
        // realized = +2800; residual = (10000 − 4000, 130000 − 52000) = (6000, 78000).
        Result result = useCase.settle(partialCmd("13.7", 4_000L));

        assertThat(result.settled()).isTrue();
        assertThat(result.realizedBaseMinor()).isEqualTo(2_800L);
        assertThat(result.proceedsBaseMinor()).isEqualTo(54_800L);
        assertThat(result.outcome()).isEqualTo(Outcome.FX_GAIN);
        assertThat(result.residualForeignMinor()).isEqualTo(6_000L);
        assertThat(result.residualCarryingBaseMinor()).isEqualTo(78_000L);

        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(postJournalEntryUseCase).post(entry.capture(), anyString(), eq(OPERATOR));
        assertThat(entry.getValue().isBalanced()).isTrue();
    }

    @Test
    @DisplayName("settleForeignAmount omitted (null) → full settlement, residual (0,0) — net-zero AC-2")
    void nullSettleAmountIsFull() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.settle(partialCmd("13.7", null));

        assertThat(result.realizedBaseMinor()).isEqualTo(7_000L);     // == full settle
        assertThat(result.proceedsBaseMinor()).isEqualTo(137_000L);
        assertThat(result.residualForeignMinor()).isZero();
        assertThat(result.residualCarryingBaseMinor()).isZero();
    }

    @Test
    @DisplayName("settleForeignAmount == |F| → full settlement, residual (0,0)")
    void exactFullAmountIsFull() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.settle(partialCmd("13.7", 10_000L));

        assertThat(result.realizedBaseMinor()).isEqualTo(7_000L);
        assertThat(result.residualForeignMinor()).isZero();
        assertThat(result.residualCarryingBaseMinor()).isZero();
    }

    @Test
    @DisplayName("settleForeignAmount zero → SETTLEMENT_AMOUNT_INVALID, no post, key NOT marked")
    void zeroSettleAmountRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));

        assertThatThrownBy(() -> useCase.settle(partialCmd("13.7", 0L)))
                .isInstanceOf(SettlementAmountInvalidException.class);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("settleForeignAmount opposite sign to F → SETTLEMENT_AMOUNT_INVALID")
    void oppositeSignRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L))); // F = +10000

        assertThatThrownBy(() -> useCase.settle(partialCmd("13.7", -4_000L)))
                .isInstanceOf(SettlementAmountInvalidException.class);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("settleForeignAmount > |F| (over-settle) → SETTLEMENT_AMOUNT_INVALID, no entry")
    void overSettleRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));

        assertThatThrownBy(() -> useCase.settle(partialCmd("13.7", 10_001L)))
                .isInstanceOf(SettlementAmountInvalidException.class);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    // ---- FIFO lot consumption (17th increment — TASK-FIN-BE-025, ADR-001 D3) ----

    @Test
    @DisplayName("FIFO config + 2 lots → C_settle from the FIFO walk (lot-exact), consumed lots saved")
    void fifoConsumesOldestFirst() {
        // Position F = 2000 USD, pool carrying C = 2,700,000 (avg 1350/USD). Two lots:
        // lot1 1000@1,300,000, lot2 1000@1,400,000. Settle 1500 @ spot 1500 (proceeds 2,250,000).
        // FIFO C_settle = 1,300,000 (lot1 full) + round(1,400,000×500/1000)=700,000 = 2,000,000.
        // Weighted-average would be round(2,700,000×1500/2000)=2,025,000 → DIFFERS (branch matters).
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(2_000L, 2_700_000L)));
        when(fxCostFlowConfigRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(FxCostFlowConfig.of(TENANT, CostFlowMethod.FIFO, "op", NOW)));
        FxPositionLot lot1 = openLot(1L, 1_000L, 1_300_000L);
        FxPositionLot lot2 = openLot(2L, 1_000L, 1_400_000L);
        when(fxPositionLotRepository.findOpenLots(TENANT, CASH, Currency.USD))
                .thenReturn(java.util.List.of(lot1, lot2));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.settle(partialCmd("1500", 1_500L));

        assertThat(result.settled()).isTrue();
        assertThat(result.proceedsBaseMinor()).isEqualTo(2_250_000L);
        // realized = proceeds − C_settle_fifo = 2,250,000 − 2,000,000 = 250,000 (FIFO, not 225,000 avg).
        assertThat(result.realizedBaseMinor()).isEqualTo(250_000L);
        assertThat(result.outcome()).isEqualTo(Outcome.FX_GAIN);
        // residual carrying = C − C_settle_fifo = 2,700,000 − 2,000,000 = 700,000 (lot-exact: lot2's remaining).
        assertThat(result.residualForeignMinor()).isEqualTo(500L);
        assertThat(result.residualCarryingBaseMinor()).isEqualTo(700_000L);
        // Both consumed lots persisted (lot1 fully, lot2 partially).
        verify(fxPositionLotRepository).save(lot1);
        verify(fxPositionLotRepository).save(lot2);
        assertThat(lot1.remainingForeignMinor()).isZero();
        assertThat(lot2.remainingForeignMinor()).isEqualTo(500L);
    }

    @Test
    @DisplayName("FIFO config but lots absent/short → safe fallback to weighted-average, no lot saved")
    void fifoShortfallFallsBackToWeightedAverage() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(10_000L, 130_000L)));
        when(fxCostFlowConfigRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(FxCostFlowConfig.of(TENANT, CostFlowMethod.FIFO, "op", NOW)));
        // No open lots for the position → Σremaining 0 < |F_settle| → fallback.
        when(fxPositionLotRepository.findOpenLots(TENANT, CASH, Currency.USD))
                .thenReturn(java.util.List.of());
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Full settle @ 13.7 — weighted-average C_settle == C == 130000; realized 7000.
        Result result = useCase.settle(cmd("13.7"));

        assertThat(result.settled()).isTrue();
        assertThat(result.realizedBaseMinor()).isEqualTo(7_000L);   // weighted-average outcome
        assertThat(result.proceedsBaseMinor()).isEqualTo(137_000L);
        verify(fxPositionLotRepository, never()).save(any());        // no lot mutation persisted
    }

    // ---- Per-account cost-flow override resolution (21st increment — TASK-FIN-BE-029) ----
    // Pure static resolver, no Testcontainers (AC-2). Precedence:
    //   account override > tenant default > WEIGHTED_AVERAGE.

    @Test
    @DisplayName("resolveCostFlowMethod: account override present → wins over the tenant default")
    void resolveAccountOverrideWins() {
        CostFlowMethod resolved = SettleForeignPositionUseCase.resolveCostFlowMethod(
                Optional.of(CostFlowMethod.FIFO), Optional.of(CostFlowMethod.WEIGHTED_AVERAGE));
        assertThat(resolved).isEqualTo(CostFlowMethod.FIFO);
    }

    @Test
    @DisplayName("resolveCostFlowMethod: account empty + tenant present → the tenant default")
    void resolveFallsBackToTenantDefault() {
        CostFlowMethod resolved = SettleForeignPositionUseCase.resolveCostFlowMethod(
                Optional.empty(), Optional.of(CostFlowMethod.FIFO));
        assertThat(resolved).isEqualTo(CostFlowMethod.FIFO);
    }

    @Test
    @DisplayName("resolveCostFlowMethod: both empty → WEIGHTED_AVERAGE")
    void resolveDefaultsToWeightedAverage() {
        CostFlowMethod resolved = SettleForeignPositionUseCase.resolveCostFlowMethod(
                Optional.empty(), Optional.empty());
        assertThat(resolved).isEqualTo(CostFlowMethod.WEIGHTED_AVERAGE);
    }

    @Test
    @DisplayName("resolveCostFlowMethod: account WEIGHTED_AVERAGE + tenant FIFO → account downgrades to WEIGHTED_AVERAGE")
    void resolveAccountCanDowngrade() {
        CostFlowMethod resolved = SettleForeignPositionUseCase.resolveCostFlowMethod(
                Optional.of(CostFlowMethod.WEIGHTED_AVERAGE), Optional.of(CostFlowMethod.FIFO));
        assertThat(resolved).isEqualTo(CostFlowMethod.WEIGHTED_AVERAGE);
    }

    @Test
    @DisplayName("an account override = FIFO (tenant unset) routes the settle through the FIFO walk")
    void accountOverrideElevatesToFifo() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(PROCEEDS, TENANT)).thenReturn(true);
        when(journalRepository.accountTotalsForCurrency(CASH, Currency.USD, TENANT))
                .thenReturn(Optional.of(usdPosition(2_000L, 2_700_000L)));
        // Tenant default UNSET; the per-account override pins this account to FIFO.
        when(fxCostFlowAccountConfigRepository.findByTenantIdAndAccountCode(TENANT, CASH))
                .thenReturn(Optional.of(FxCostFlowAccountConfig.of(
                        TENANT, CASH, CostFlowMethod.FIFO, "op", NOW)));
        FxPositionLot lot1 = openLot(1L, 1_000L, 1_300_000L);
        FxPositionLot lot2 = openLot(2L, 1_000L, 1_400_000L);
        when(fxPositionLotRepository.findOpenLots(TENANT, CASH, Currency.USD))
                .thenReturn(List.of(lot1, lot2));
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.settle(partialCmd("1500", 1_500L));

        // FIFO C_settle = 2,000,000 → realized 250,000 (NOT the weighted-average 225,000): the
        // override drove the FIFO branch even with the tenant default unset.
        assertThat(result.realizedBaseMinor()).isEqualTo(250_000L);
        assertThat(result.residualCarryingBaseMinor()).isEqualTo(700_000L);
        verify(fxPositionLotRepository).save(lot1);
        verify(fxPositionLotRepository).save(lot2);
        // The tenant default is NOT consulted as the deciding factor here — the account override
        // resolves first (it is still queried, but lenient stubbing covers the empty fallthrough).
    }

    /** A fully-open lot for FIFO-walk stubbing (acquired_at ordered by seq). */
    private static FxPositionLot openLot(long seq, long foreignMinor, long baseMinor) {
        return FxPositionLot.acquire(TENANT, CASH, Currency.USD,
                Instant.parse("2026-06-01T00:00:00Z").plusSeconds(seq), seq,
                foreignMinor, baseMinor, "entry-" + seq, NOW);
    }

    private static List<JournalLine> settlementLines() {
        Money base130 = Money.of(130_000L, Currency.KRW);
        Money base137 = Money.of(137_000L, Currency.KRW);
        Money base7 = Money.of(7_000L, Currency.KRW);
        return List.of(
                JournalLine.of(TENANT, CASH, EntryDirection.CREDIT,
                        Money.of(10_000L, Currency.USD), base130),
                JournalLine.of(TENANT, PROCEEDS, EntryDirection.DEBIT, base137),
                JournalLine.credit(TENANT, LedgerAccountCodes.FX_GAIN, base7));
    }
}
