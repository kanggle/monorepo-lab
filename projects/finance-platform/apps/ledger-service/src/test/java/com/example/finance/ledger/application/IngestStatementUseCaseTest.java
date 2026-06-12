package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.LedgerEventPublisher;
import com.example.finance.ledger.application.view.StatementView;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationAccountInvalidException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationPeriodLockedException;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.InternalLine;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.StatementSource;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class IngestStatementUseCaseTest {

    private static final String TENANT = "finance";
    private static final String CODE = LedgerAccountCodes.CASH_CLEARING;
    private static final Instant NOW = Instant.parse("2026-01-31T00:00:00Z");
    private static final LocalDate STMT_DATE = LocalDate.parse("2026-01-31");
    /** STMT_DATE mapped to start-of-day UTC instant (the period-lock key). */
    private static final Instant STMT_DATE_INSTANT = Instant.parse("2026-01-31T00:00:00Z");
    private static final LocalDate VALUE_DATE = LocalDate.parse("2026-01-15");

    @Mock ReconciliationRepository reconciliationRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock LedgerEventPublisher ledgerEventPublisher;
    @Mock ClockPort clock;
    @Mock AccountingPeriodRepository accountingPeriodRepository;

    IngestStatementUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new IngestStatementUseCase(
                reconciliationRepository, auditLogRepository, ledgerEventPublisher, clock,
                accountingPeriodRepository);
    }

    /** Builds a closed AccountingPeriod covering {@code at} (from=at, to=at+1day). */
    private static AccountingPeriod closedPeriodCovering(Instant at) {
        AccountingPeriod period = AccountingPeriod.open(
                "period-1", TENANT, at, at.plusSeconds(86_400));
        period.close(at.plusSeconds(86_400), "closer", 3L);
        return period;
    }

    private static Money krw(long m) {
        return Money.of(m, Currency.KRW);
    }

    private IngestStatementCommand command(List<IngestStatementCommand.Line> lines) {
        return new IngestStatementCommand(TENANT, CODE, StatementSource.BANK, STMT_DATE,
                lines, "user-1");
    }

    private static IngestStatementCommand.Line line(String ref, long amount, EntryDirection dir) {
        return new IngestStatementCommand.Line(ref, krw(amount), dir, VALUE_DATE, null, null);
    }

    @Test
    @DisplayName("ingest persists matches + OPEN discrepancies, emits completed + N detected, NO auto-close")
    void ingestPersistsAndEmitsNoAutoClose() {
        // One external line matches an internal entry (150000 DEBIT); a second
        // external (70000 DEBIT) is unmatched; an internal (99000 DEBIT) is unmatched.
        when(accountingPeriodRepository.findCovering(TENANT, STMT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty()); // net-zero: no closed period
        when(clock.now()).thenReturn(NOW);
        when(reconciliationRepository.saveStatement(any())).thenAnswer(i -> i.getArgument(0));
        when(reconciliationRepository.findUnmatchedInternalLines(TENANT, CODE)).thenReturn(List.of(
                new InternalLine("entry-a", CODE, EntryDirection.DEBIT, krw(150_000), krw(150_000)),
                new InternalLine("entry-b", CODE, EntryDirection.DEBIT, krw(99_000), krw(99_000))));
        when(reconciliationRepository.saveDiscrepancies(any())).thenAnswer(i -> i.getArgument(0));

        StatementView view = useCase.ingest(command(List.of(
                line("R1", 150_000, EntryDirection.DEBIT),
                line("R2", 70_000, EntryDirection.DEBIT))));

        assertThat(view.matchedCount()).isEqualTo(1);
        assertThat(view.discrepancyCount()).isEqualTo(2);

        // matches persisted (one)
        verify(reconciliationRepository).saveMatches(any());

        // discrepancies persisted, ALL OPEN (F8 — never auto-closed)
        ArgumentCaptor<List<ReconciliationDiscrepancy>> disc = ArgumentCaptor.forClass(List.class);
        verify(reconciliationRepository).saveDiscrepancies(disc.capture());
        assertThat(disc.getValue()).hasSize(2);
        assertThat(disc.getValue()).allMatch(d -> d.status() == DiscrepancyStatus.OPEN);
        assertThat(disc.getValue()).allMatch(ReconciliationDiscrepancy::isOpen);
        assertThat(disc.getValue()).noneMatch(d -> d.resolutionType() != null);

        // emission: one completed + one detected per discrepancy
        verify(ledgerEventPublisher).publishReconciliationCompleted(any(), eq(1), eq(2));
        verify(ledgerEventPublisher, times(2)).publishDiscrepancyDetected(any());

        // audit row written
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("INGESTED");
    }

    @Test
    @DisplayName("all-matched statement emits completed with discrepancyCount 0 and no detected")
    void allMatchedZeroDiscrepancies() {
        when(accountingPeriodRepository.findCovering(TENANT, STMT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty()); // net-zero: no closed period
        when(clock.now()).thenReturn(NOW);
        when(reconciliationRepository.saveStatement(any())).thenAnswer(i -> i.getArgument(0));
        when(reconciliationRepository.findUnmatchedInternalLines(TENANT, CODE)).thenReturn(List.of(
                new InternalLine("entry-a", CODE, EntryDirection.DEBIT, krw(150_000), krw(150_000))));
        when(reconciliationRepository.saveDiscrepancies(any())).thenAnswer(i -> i.getArgument(0));

        StatementView view = useCase.ingest(command(List.of(
                line("R1", 150_000, EntryDirection.DEBIT))));

        assertThat(view.matchedCount()).isEqualTo(1);
        assertThat(view.discrepancyCount()).isZero();
        verify(ledgerEventPublisher).publishReconciliationCompleted(any(), eq(1), eq(0));
        verify(ledgerEventPublisher, never()).publishDiscrepancyDetected(any());
    }

    @Test
    @DisplayName("ingest on a non-clearing account → RECONCILIATION_ACCOUNT_INVALID, no persist/emit")
    void nonClearingAccountRejected() {
        IngestStatementCommand cmd = new IngestStatementCommand(
                TENANT, LedgerAccountCodes.customerWallet("acc-1"), StatementSource.BANK,
                STMT_DATE, List.of(line("R1", 150_000, EntryDirection.DEBIT)), "user-1");

        assertThatThrownBy(() -> useCase.ingest(cmd))
                .isInstanceOf(ReconciliationAccountInvalidException.class);

        verify(reconciliationRepository, never()).saveStatement(any());
        verify(reconciliationRepository, never()).saveMatches(any());
        verify(reconciliationRepository, never()).saveDiscrepancies(any());
        verifyNoInteractions(ledgerEventPublisher);
        verifyNoInteractions(auditLogRepository);
    }

    // ---- 7th increment: ingest-time period lock (TASK-FIN-BE-013) ----

    @Test
    @DisplayName("(AC-1) statement date covered by a CLOSED period → RECONCILIATION_PERIOD_LOCKED; " +
            "saveStatement / saveMatches / saveDiscrepancies / audit / emit NEVER called")
    void ingestIntoClosedPeriodIsRejected() {
        when(accountingPeriodRepository.findCovering(TENANT, STMT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.of(closedPeriodCovering(STMT_DATE_INSTANT)));

        assertThatThrownBy(() -> useCase.ingest(command(List.of(
                line("R1", 150_000, EntryDirection.DEBIT)))))
                .isInstanceOf(ReconciliationPeriodLockedException.class)
                .hasMessageContaining("CLOSED")
                .hasMessageContaining("2026-01-31");

        // Guard runs before any write — no statement, no matches, no discrepancies, no audit, no events.
        verify(reconciliationRepository, never()).saveStatement(any());
        verify(reconciliationRepository, never()).saveMatches(any());
        verify(reconciliationRepository, never()).saveDiscrepancies(any());
        verifyNoInteractions(auditLogRepository);
        verifyNoInteractions(ledgerEventPublisher);
        // clock.now() was never reached either.
        verify(clock, never()).now();
    }

    @Test
    @DisplayName("(AC-2) no covering CLOSED period (findCovering empty) → ingest proceeds normally (net-zero)")
    void noCoveringPeriodIngestsNormally() {
        when(accountingPeriodRepository.findCovering(TENANT, STMT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty()); // net-zero: no closed period
        when(clock.now()).thenReturn(NOW);
        when(reconciliationRepository.saveStatement(any())).thenAnswer(i -> i.getArgument(0));
        when(reconciliationRepository.findUnmatchedInternalLines(TENANT, CODE)).thenReturn(List.of());
        when(reconciliationRepository.saveDiscrepancies(any())).thenAnswer(i -> i.getArgument(0));

        StatementView view = useCase.ingest(command(List.of(
                line("R1", 50_000, EntryDirection.DEBIT))));

        // Statement persisted + emit happened normally.
        assertThat(view.matchedCount()).isZero();
        assertThat(view.discrepancyCount()).isEqualTo(1); // UNMATCHED_EXTERNAL for R1
        verify(reconciliationRepository, times(2)).saveStatement(any());
        verify(ledgerEventPublisher).publishReconciliationCompleted(any(), eq(0), eq(1));
    }

    @Test
    @DisplayName("(AC-2 boundary) statementDate exactly on a period's from-day (start-of-day UTC) → CLOSED period locks the ingest")
    void boundaryDateOnPeriodFromDayLocks() {
        // Period [Jan 31 00:00Z, Feb 1 00:00Z); statementDate=Jan 31 → mapped to Jan 31 00:00Z (from-inclusive).
        Instant fromInstant = STMT_DATE_INSTANT; // = 2026-01-31T00:00:00Z
        AccountingPeriod period = closedPeriodCovering(fromInstant);
        // Sanity: the period's from instant equals the mapped statement-date instant, and covers it.
        assertThat(period.from()).isEqualTo(fromInstant);
        assertThat(period.covers(fromInstant)).isTrue();

        when(accountingPeriodRepository.findCovering(TENANT, fromInstant, PeriodStatus.CLOSED))
                .thenReturn(Optional.of(period));

        assertThatThrownBy(() -> useCase.ingest(command(List.of(
                line("R1", 70_000, EntryDirection.DEBIT)))))
                .isInstanceOf(ReconciliationPeriodLockedException.class);

        // Nothing was written.
        verify(reconciliationRepository, never()).saveStatement(any());
        verifyNoInteractions(auditLogRepository);
        verifyNoInteractions(ledgerEventPublisher);
    }

    @Test
    @DisplayName("(AC-1) RECONCILIATION_ACCOUNT_INVALID takes precedence over the period lock " +
            "(account check runs first; period repo never consulted for a non-clearing account)")
    void nonClearingAccountRejectedBeforePeriodCheck() {
        // A non-clearing account: the account-validity check fires first; the period guard
        // must NOT be consulted (the `@MockitoSettings STRICT_STUBS` would fail if findCovering
        // were stubbed but not called — leave it unstubbed and verify it's never invoked).
        IngestStatementCommand cmd = new IngestStatementCommand(
                TENANT, LedgerAccountCodes.customerWallet("acc-x"), StatementSource.BANK,
                STMT_DATE, List.of(line("R1", 50_000, EntryDirection.DEBIT)), "user-1");

        assertThatThrownBy(() -> useCase.ingest(cmd))
                .isInstanceOf(ReconciliationAccountInvalidException.class);

        // Period repo never touched — the account guard short-circuited first.
        verifyNoInteractions(accountingPeriodRepository);
        verify(reconciliationRepository, never()).saveStatement(any());
    }
}
