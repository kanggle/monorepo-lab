package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.LedgerEventPublisher;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerPeriodClosedException;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The closed-period posting guard on the single guarded write path
 * (architecture.md § Accounting Period § Posting guard, AC-3). A CLOSED period
 * covering {@code postedAt} rejects the posting; with no covering closed period —
 * including no period at all — posting proceeds byte-identically (net-zero).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PostJournalEntryUseCaseGuardTest {

    private static final String TENANT = "finance";
    private static final Money KRW_150K = Money.of(150_000L, Currency.KRW);
    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");
    private static final Instant POSTED_AT = Instant.parse("2026-01-15T00:00:00Z");

    @Mock JournalRepository journalRepository;
    @Mock LedgerAccountRepository ledgerAccountRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock AccountingPeriodRepository accountingPeriodRepository;
    @Mock LedgerEventPublisher ledgerEventPublisher;
    @Mock ClockPort clock;

    PostJournalEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PostJournalEntryUseCase(journalRepository, ledgerAccountRepository,
                auditLogRepository, accountingPeriodRepository, ledgerEventPublisher, clock);
    }

    private static JournalEntry entry() {
        return JournalEntry.post("e-1", TENANT, POSTED_AT,
                SourceRef.ofTransaction("txn-1", "evt-1"), List.of(
                        JournalLine.debit(TENANT, LedgerAccountCodes.CASH_CLEARING, KRW_150K),
                        JournalLine.credit(TENANT, LedgerAccountCodes.customerWallet("acc-1"), KRW_150K)));
    }

    @Test
    @DisplayName("a CLOSED period covering postedAt → LedgerPeriodClosedException, nothing persisted")
    void closedCoveringRejects() {
        when(clock.now()).thenReturn(NOW);
        AccountingPeriod closed = AccountingPeriod.open("p-1", TENANT,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"));
        closed.close(NOW, "user-0", 0L);
        when(accountingPeriodRepository.findCovering(TENANT, POSTED_AT, PeriodStatus.CLOSED))
                .thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> useCase.post(entry(), "reason"))
                .isInstanceOf(LedgerPeriodClosedException.class);

        verify(journalRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
        verify(ledgerAccountRepository, never()).save(any());
        // AC-3: a guard-rejected posting appends NO outbox row (the whole Tx rolls back).
        verify(ledgerEventPublisher, never()).publishEntryPosted(any());
    }

    @Test
    @DisplayName("no covering closed period (empty) → posting proceeds normally (net-zero)")
    void noCoveringProceeds() {
        when(clock.now()).thenReturn(NOW);
        when(accountingPeriodRepository.findCovering(TENANT, POSTED_AT, PeriodStatus.CLOSED))
                .thenReturn(Optional.empty());
        when(ledgerAccountRepository.existsByCode(anyString(), eq(TENANT))).thenReturn(true);
        when(journalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry saved = useCase.post(entry(), "reason");

        verify(journalRepository).save(saved);
        verify(auditLogRepository).save(any());
        // AC-1: every posted entry appends exactly one entry.posted outbox row,
        // after the entry+audit save, in the same transaction.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(
                journalRepository, auditLogRepository, ledgerEventPublisher);
        inOrder.verify(journalRepository).save(saved);
        inOrder.verify(auditLogRepository).save(any());
        inOrder.verify(ledgerEventPublisher).publishEntryPosted(saved);
    }
}
