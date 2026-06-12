package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.DiscrepancyView;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationAlreadyResolvedException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationDiscrepancyNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationPeriodLockedException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyType;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ResolutionType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ResolveDiscrepancyUseCaseTest {

    private static final String TENANT = "finance";
    private static final String CODE = LedgerAccountCodes.CASH_CLEARING;
    private static final Instant DETECTED = Instant.parse("2026-01-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");
    private static final String STATEMENT_ID = "stmt-1";
    /** The statement date the discrepancy's owning statement carries. */
    private static final LocalDate STATEMENT_DATE = LocalDate.parse("2026-01-31");
    /** STATEMENT_DATE mapped to its start-of-day UTC instant (the period-lock key). */
    private static final Instant STATEMENT_DATE_INSTANT = Instant.parse("2026-01-31T00:00:00Z");

    @Mock ReconciliationRepository reconciliationRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock AccountingPeriodRepository accountingPeriodRepository;
    @Mock ClockPort clock;

    ResolveDiscrepancyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResolveDiscrepancyUseCase(
                reconciliationRepository, auditLogRepository,
                accountingPeriodRepository, clock);
    }

    private static ReconciliationDiscrepancy openDiscrepancy() {
        return ReconciliationDiscrepancy.open("disc-1", TENANT, STATEMENT_ID, CODE,
                DiscrepancyType.UNMATCHED_EXTERNAL, "R2", null,
                99_000L, 0L, Currency.KRW, DETECTED);
    }

    private static ReconciliationDiscrepancy openDiscrepancyNoStatement() {
        return ReconciliationDiscrepancy.open("disc-1", TENANT, null, CODE,
                DiscrepancyType.UNMATCHED_EXTERNAL, "R2", null,
                99_000L, 0L, Currency.KRW, DETECTED);
    }

    /** A statement dated {@code date} (the period-lock guard reads only its statementDate). */
    private static ExternalStatement statement(LocalDate date) {
        return ExternalStatement.open(STATEMENT_ID, TENANT, CODE, StatementSource.BANK,
                date, Instant.parse("2026-02-01T00:00:00Z"), List.of());
    }

    private static AccountingPeriod closedPeriodCovering(Instant at) {
        AccountingPeriod period = AccountingPeriod.open(
                "period-1", TENANT, at, at.plusSeconds(86_400));
        period.close(at.plusSeconds(86_400), "closer", 3L);
        return period;
    }

    @Test
    @DisplayName("resolve flips OPEN→RESOLVED + resolution record + audit")
    void resolveOpen() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.findStatementById(STATEMENT_ID, TENANT))
                .thenReturn(Optional.of(statement(STATEMENT_DATE)));
        when(accountingPeriodRepository.findCovering(TENANT, STATEMENT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty());
        when(reconciliationRepository.saveDiscrepancy(any())).thenAnswer(i -> i.getArgument(0));
        when(clock.now()).thenReturn(NOW);

        DiscrepancyView view = useCase.resolve("disc-1", TENANT,
                ResolutionType.WRITTEN_OFF, "bank fee, below threshold", "user-1");

        assertThat(view.status()).isEqualTo(DiscrepancyStatus.RESOLVED);
        assertThat(view.resolutionType()).isEqualTo(ResolutionType.WRITTEN_OFF);
        assertThat(view.note()).isEqualTo("bank fee, below threshold");
        assertThat(view.resolvedBy()).isEqualTo("user-1");
        assertThat(view.resolvedAt()).isEqualTo(NOW);

        verify(reconciliationRepository).saveDiscrepancy(discrepancy);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("a second resolve on an already-RESOLVED discrepancy → ReconciliationAlreadyResolvedException")
    void reResolveRejected() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        discrepancy.resolve(ResolutionType.ACCEPTED, "first", "user-0", NOW);
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        // Guard is net-zero (no covering CLOSED period) → resolve() then throws AlreadyResolved.
        when(reconciliationRepository.findStatementById(STATEMENT_ID, TENANT))
                .thenReturn(Optional.of(statement(STATEMENT_DATE)));
        when(accountingPeriodRepository.findCovering(TENANT, STATEMENT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty());
        when(clock.now()).thenReturn(NOW);

        assertThatThrownBy(() -> useCase.resolve("disc-1", TENANT,
                ResolutionType.WRITTEN_OFF, "second", "user-1"))
                .isInstanceOf(ReconciliationAlreadyResolvedException.class);

        verify(reconciliationRepository, never()).saveDiscrepancy(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown discrepancy id → ReconciliationDiscrepancyNotFoundException")
    void notFound() {
        when(reconciliationRepository.findDiscrepancyById("missing", TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.resolve("missing", TENANT,
                ResolutionType.ACCEPTED, null, "user-1"))
                .isInstanceOf(ReconciliationDiscrepancyNotFoundException.class);

        verify(reconciliationRepository, never()).saveDiscrepancy(any());
        verify(auditLogRepository, never()).save(any());
    }

    // ---- 6th increment: period lock (TASK-FIN-BE-012) ----

    @Test
    @DisplayName("(AC-1) statement date in a CLOSED period → RECONCILIATION_PERIOD_LOCKED, no mutation/audit (stays OPEN)")
    void periodLockedRejectsResolve() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.findStatementById(STATEMENT_ID, TENANT))
                .thenReturn(Optional.of(statement(STATEMENT_DATE)));
        when(accountingPeriodRepository.findCovering(TENANT, STATEMENT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.of(closedPeriodCovering(STATEMENT_DATE_INSTANT)));

        assertThatThrownBy(() -> useCase.resolve("disc-1", TENANT,
                ResolutionType.WRITTEN_OFF, "late", "user-1"))
                .isInstanceOf(ReconciliationPeriodLockedException.class);

        // No mutation, no audit — the discrepancy stays OPEN (clock never consulted).
        assertThat(discrepancy.status()).isEqualTo(DiscrepancyStatus.OPEN);
        verify(reconciliationRepository, never()).saveDiscrepancy(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("(AC-1 boundary) a statement dated exactly on the period's from-day maps into that period (start-of-day UTC, from-inclusive)")
    void periodLockBoundaryFromDayInclusive() {
        // Period [Jan 31 00:00Z, Feb 1 00:00Z); statement dated Jan 31 maps to Jan 31 00:00Z = from (inclusive).
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.findStatementById(STATEMENT_ID, TENANT))
                .thenReturn(Optional.of(statement(STATEMENT_DATE)));
        AccountingPeriod period = closedPeriodCovering(STATEMENT_DATE_INSTANT);
        // sanity: the period's from instant equals the mapped statement-date instant.
        assertThat(period.from()).isEqualTo(STATEMENT_DATE_INSTANT);
        assertThat(period.covers(STATEMENT_DATE_INSTANT)).isTrue();
        when(accountingPeriodRepository.findCovering(TENANT, STATEMENT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.of(period));

        assertThatThrownBy(() -> useCase.resolve("disc-1", TENANT,
                ResolutionType.WRITTEN_OFF, "boundary", "user-1"))
                .isInstanceOf(ReconciliationPeriodLockedException.class);
        verify(reconciliationRepository, never()).saveDiscrepancy(any());
    }

    @Test
    @DisplayName("(AC-2) no covering CLOSED period (findCovering empty) → resolves normally (net-zero)")
    void noCoveringPeriodResolves() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.findStatementById(STATEMENT_ID, TENANT))
                .thenReturn(Optional.of(statement(STATEMENT_DATE)));
        when(accountingPeriodRepository.findCovering(TENANT, STATEMENT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty());
        when(reconciliationRepository.saveDiscrepancy(any())).thenAnswer(i -> i.getArgument(0));
        when(clock.now()).thenReturn(NOW);

        DiscrepancyView view = useCase.resolve("disc-1", TENANT,
                ResolutionType.ACCEPTED, null, "user-1");

        assertThat(view.status()).isEqualTo(DiscrepancyStatus.RESOLVED);
        verify(reconciliationRepository).saveDiscrepancy(discrepancy);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("(AC-2) an OPEN period covering the date does NOT lock — the guard checks only CLOSED (net-zero)")
    void openPeriodDoesNotLock() {
        // The guard queries findCovering(..., CLOSED) only; an OPEN period covering the
        // date never surfaces in that query → empty → resolves.
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.findStatementById(STATEMENT_ID, TENANT))
                .thenReturn(Optional.of(statement(STATEMENT_DATE)));
        when(accountingPeriodRepository.findCovering(TENANT, STATEMENT_DATE_INSTANT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty());
        when(reconciliationRepository.saveDiscrepancy(any())).thenAnswer(i -> i.getArgument(0));
        when(clock.now()).thenReturn(NOW);

        DiscrepancyView view = useCase.resolve("disc-1", TENANT,
                ResolutionType.ACCEPTED, null, "user-1");

        assertThat(view.status()).isEqualTo(DiscrepancyStatus.RESOLVED);
        verify(reconciliationRepository).saveDiscrepancy(discrepancy);
    }

    @Test
    @DisplayName("(AC-2) null statementId → no statement lookup, resolves (net-zero)")
    void nullStatementIdResolves() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancyNoStatement();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.saveDiscrepancy(any())).thenAnswer(i -> i.getArgument(0));
        when(clock.now()).thenReturn(NOW);

        DiscrepancyView view = useCase.resolve("disc-1", TENANT,
                ResolutionType.ACCEPTED, null, "user-1");

        assertThat(view.status()).isEqualTo(DiscrepancyStatus.RESOLVED);
        // The guard short-circuited before any statement / period lookup.
        verify(reconciliationRepository, never()).findStatementById(any(), any());
        verify(accountingPeriodRepository, never()).findCovering(any(), any(), any());
        verify(reconciliationRepository).saveDiscrepancy(discrepancy);
    }

    @Test
    @DisplayName("(AC-2) statement absent → no period lookup, resolves (net-zero)")
    void statementAbsentResolves() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.findStatementById(STATEMENT_ID, TENANT))
                .thenReturn(Optional.empty());
        when(reconciliationRepository.saveDiscrepancy(any())).thenAnswer(i -> i.getArgument(0));
        when(clock.now()).thenReturn(NOW);

        DiscrepancyView view = useCase.resolve("disc-1", TENANT,
                ResolutionType.ACCEPTED, null, "user-1");

        assertThat(view.status()).isEqualTo(DiscrepancyStatus.RESOLVED);
        verify(accountingPeriodRepository, never()).findCovering(any(), any(), any());
        verify(reconciliationRepository).saveDiscrepancy(discrepancy);
    }
}
