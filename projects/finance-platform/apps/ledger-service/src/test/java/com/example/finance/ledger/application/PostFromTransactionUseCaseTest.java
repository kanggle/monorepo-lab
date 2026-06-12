package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.journal.CompletedTransaction;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.LedgerTransactionType;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PostFromTransactionUseCaseTest {

    private static final String TENANT = "finance";
    private static final Money MONEY = Money.of(150_000L, Currency.KRW);
    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Mock PostJournalEntryUseCase postJournalEntry;
    @Mock JournalRepository journalRepository;
    @Mock ProcessedEventStore processedEvents;
    @Mock ClockPort clock;

    PostFromTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PostFromTransactionUseCase(
                postJournalEntry, journalRepository, processedEvents, clock);
    }

    private static CompletedTransaction txn(LedgerTransactionType type, String counterparty) {
        return new CompletedTransaction(TENANT, "txn-1", "acc-A", type, MONEY, counterparty);
    }

    @Test
    @DisplayName("a completed TOPUP maps via the policy, posts once, and records the dedupe row")
    void completedTopupPosts() {
        when(processedEvents.isProcessed("evt-1")).thenReturn(false);
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntry.post(any(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        Optional<JournalEntry> posted = useCase.post(PostFromTransactionCommand.completed(
                "evt-1", "finance.transaction.completed.v1",
                txn(LedgerTransactionType.TOPUP, null)));

        assertThat(posted).isPresent();
        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(postJournalEntry).post(entry.capture(), anyString());
        assertThat(entry.getValue().isBalanced()).isTrue();
        verify(processedEvents).markProcessed("evt-1", TENANT,
                "finance.transaction.completed.v1", "txn-1", NOW);
    }

    @Test
    @DisplayName("a duplicate eventId is a no-op — no post, no dedupe insert (STRICT_STUBS)")
    void duplicateIsNoOp() {
        when(processedEvents.isProcessed("evt-1")).thenReturn(true);

        Optional<JournalEntry> posted = useCase.post(PostFromTransactionCommand.completed(
                "evt-1", "finance.transaction.completed.v1",
                txn(LedgerTransactionType.TOPUP, null)));

        assertThat(posted).isEmpty();
        verify(postJournalEntry, never()).post(any(), anyString());
        verify(processedEvents, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("HOLD posts no entry but still records the dedupe row (processed no-op)")
    void holdNoEntryButDeduped() {
        when(processedEvents.isProcessed("evt-1")).thenReturn(false);
        when(clock.now()).thenReturn(NOW);

        Optional<JournalEntry> posted = useCase.post(PostFromTransactionCommand.completed(
                "evt-1", "finance.transaction.completed.v1",
                txn(LedgerTransactionType.HOLD, null)));

        assertThat(posted).isEmpty();
        verify(postJournalEntry, never()).post(any(), anyString());
        verify(processedEvents).markProcessed("evt-1", TENANT,
                "finance.transaction.completed.v1", "txn-1", NOW);
    }

    @Test
    @DisplayName("a reversal looks up the original by source txn id and posts a swapped entry")
    void reversalSwapsOriginal() {
        JournalEntry original = JournalEntry.post("e-1", TENANT, NOW,
                SourceRef.ofTransaction("orig-txn", "orig-evt"), List.of(
                        JournalLine.debit(TENANT, LedgerAccountCodes.CASH_CLEARING, MONEY),
                        JournalLine.credit(TENANT, LedgerAccountCodes.customerWallet("acc-A"), MONEY)));
        when(processedEvents.isProcessed("rev-evt")).thenReturn(false);
        when(clock.now()).thenReturn(NOW);
        when(journalRepository.findBySourceTransactionId("orig-txn", TENANT))
                .thenReturn(Optional.of(original));
        when(postJournalEntry.post(any(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        Optional<JournalEntry> posted = useCase.post(PostFromTransactionCommand.reversed(
                "rev-evt", "finance.transaction.reversed.v1", "orig-txn",
                txn(LedgerTransactionType.REVERSAL, null)));

        assertThat(posted).isPresent();
        JournalEntry reversal = posted.get();
        assertThat(reversal.reversalOfEntryId()).isEqualTo("e-1");
        JournalLine cash = reversal.lines().stream()
                .filter(l -> l.ledgerAccountCode().equals(LedgerAccountCodes.CASH_CLEARING))
                .findFirst().orElseThrow();
        assertThat(cash.direction()).isEqualTo(EntryDirection.CREDIT);
        verify(processedEvents).markProcessed("rev-evt", TENANT,
                "finance.transaction.reversed.v1", "txn-1", NOW);
    }

    @Test
    @DisplayName("a reversal whose original entry is absent → JournalEntryNotFoundException (→ DLT)")
    void reversalMissingOriginal() {
        when(processedEvents.isProcessed("rev-evt")).thenReturn(false);
        when(clock.now()).thenReturn(NOW);
        when(journalRepository.findBySourceTransactionId("orig-txn", TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.post(PostFromTransactionCommand.reversed(
                "rev-evt", "finance.transaction.reversed.v1", "orig-txn",
                txn(LedgerTransactionType.REVERSAL, null))))
                .isInstanceOf(JournalEntryNotFoundException.class);

        verify(postJournalEntry, never()).post(any(), anyString());
    }
}
