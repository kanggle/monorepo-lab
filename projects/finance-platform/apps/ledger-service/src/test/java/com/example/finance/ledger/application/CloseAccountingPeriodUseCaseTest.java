package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.AccountingPeriodView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodAlreadyClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodNotFoundException;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository.AccountTotals;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CloseAccountingPeriodUseCaseTest {

    private static final String TENANT = "finance";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-02-01T01:00:00Z");

    @Mock AccountingPeriodRepository periodRepository;
    @Mock JournalRepository journalRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;

    CloseAccountingPeriodUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CloseAccountingPeriodUseCase(
                periodRepository, journalRepository, auditLogRepository, clock);
    }

    @Test
    @DisplayName("close computes the snapshot from postedAt<to totals, flips CLOSED, persists + audits")
    void closeComputesSnapshot() {
        AccountingPeriod period = AccountingPeriod.open("p-1", TENANT, FROM, TO);
        when(periodRepository.findById("p-1", TENANT)).thenReturn(Optional.of(period));
        when(journalRepository.accountTotalsUpTo(TENANT, TO)).thenReturn(List.of(
                new AccountTotals("CASH_CLEARING", "KRW", 150_000L, 0L),
                new AccountTotals("CUSTOMER_WALLET:acc-1", "KRW", 0L, 150_000L)));
        when(journalRepository.countEntriesUpTo(TENANT, TO)).thenReturn(1L);
        when(clock.now()).thenReturn(NOW);

        AccountingPeriodView view = useCase.close("p-1", TENANT, "user-1");

        assertThat(view.status()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(view.entryCount()).isEqualTo(1L);
        assertThat(view.closedBy()).isEqualTo("user-1");
        assertThat(view.snapshot()).isNotNull();
        assertThat(view.snapshot().inBalance()).isTrue();
        assertThat(view.snapshot().grandDebitTotal()).isEqualTo(Money.of(150_000L, Currency.KRW));
        assertThat(view.snapshot().grandCreditTotal()).isEqualTo(Money.of(150_000L, Currency.KRW));
        assertThat(view.snapshot().accounts()).hasSize(2);

        // the aggregate is persisted CLOSED and the snapshot rows saved
        verify(periodRepository).save(period);
        ArgumentCaptor<PeriodBalanceSnapshot> snap = ArgumentCaptor.forClass(PeriodBalanceSnapshot.class);
        verify(periodRepository).saveSnapshot(eq("p-1"), eq(TENANT), snap.capture());
        assertThat(snap.getValue().inBalance()).isTrue();

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("an empty period (no in-window entries) closes in balance with entryCount 0")
    void emptyPeriodClosesInBalance() {
        AccountingPeriod period = AccountingPeriod.open("p-1", TENANT, FROM, TO);
        when(periodRepository.findById("p-1", TENANT)).thenReturn(Optional.of(period));
        when(journalRepository.accountTotalsUpTo(TENANT, TO)).thenReturn(List.of());
        when(journalRepository.countEntriesUpTo(TENANT, TO)).thenReturn(0L);
        when(clock.now()).thenReturn(NOW);

        AccountingPeriodView view = useCase.close("p-1", TENANT, "user-1");

        assertThat(view.entryCount()).isEqualTo(0L);
        assertThat(view.snapshot().inBalance()).isTrue();
        assertThat(view.snapshot().accounts()).isEmpty();
        assertThat(view.snapshot().grandDebitTotal()).isEqualTo(Money.zero(Currency.KRW));
    }

    @Test
    @DisplayName("an unknown period id → AccountingPeriodNotFoundException")
    void notFound() {
        when(periodRepository.findById("missing", TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.close("missing", TENANT, "user-1"))
                .isInstanceOf(AccountingPeriodNotFoundException.class);
        verify(periodRepository, never()).save(any());
    }

    @Test
    @DisplayName("closing an already-CLOSED period → AccountingPeriodAlreadyClosedException")
    void alreadyClosed() {
        AccountingPeriod period = AccountingPeriod.open("p-1", TENANT, FROM, TO);
        period.close(NOW, "user-0", 0L);
        when(periodRepository.findById("p-1", TENANT)).thenReturn(Optional.of(period));
        when(journalRepository.accountTotalsUpTo(TENANT, TO)).thenReturn(List.of());
        when(journalRepository.countEntriesUpTo(TENANT, TO)).thenReturn(0L);
        when(clock.now()).thenReturn(NOW);

        assertThatThrownBy(() -> useCase.close("p-1", TENANT, "user-1"))
                .isInstanceOf(AccountingPeriodAlreadyClosedException.class);

        verify(periodRepository, never()).save(any());
        verify(periodRepository, never()).saveSnapshot(any(), any(), any());
    }
}
