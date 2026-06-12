package com.example.finance.ledger.application;

import com.example.finance.ledger.application.PostManualJournalEntryCommand.ManualLine;
import com.example.finance.ledger.application.PostManualJournalEntryUseCase.Result;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerEntryUnbalancedException;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
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

/**
 * Unit test for {@link PostManualJournalEntryUseCase} (5th increment,
 * TASK-FIN-BE-011 AC-1..AC-5). Mocks all ports — proves the manual path funnels a
 * balanced entry through {@code PostJournalEntryUseCase.post(entry, reason, actor)}
 * with the operator subject as actor, rejects unknown accounts (no lazy mint) and
 * unbalanced sets, and replays without a second post.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PostManualJournalEntryUseCaseTest {

    private static final String TENANT = "finance";
    private static final String OPERATOR = "operator-7";
    private static final String KEY = "ADJ-2026-06-CORR-014";
    private static final String DEDUPE = "manual:" + KEY;
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;
    private static final String WALLET = LedgerAccountCodes.customerWallet("acc-1");
    private static final Money KRW_50K = Money.of(50_000L, Currency.KRW);
    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Mock JournalRepository journalRepository;
    @Mock com.example.finance.ledger.domain.account.repository.LedgerAccountRepository ledgerAccountRepository;
    @Mock ProcessedEventStore processedEventStore;
    @Mock PostJournalEntryUseCase postJournalEntryUseCase;
    @Mock ClockPort clock;

    PostManualJournalEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PostManualJournalEntryUseCase(
                journalRepository, ledgerAccountRepository, processedEventStore,
                postJournalEntryUseCase, clock);
    }

    private static PostManualJournalEntryCommand cmd(List<ManualLine> lines) {
        return new PostManualJournalEntryCommand(
                TENANT, OPERATOR, KEY, NOW, KEY, "correct mis-posting", lines);
    }

    private static ManualLine line(String code, EntryDirection dir, Money money) {
        return new ManualLine(code, dir, money);
    }

    private static List<ManualLine> balancedLines() {
        return List.of(
                line(CASH, EntryDirection.DEBIT, KRW_50K),
                line(WALLET, EntryDirection.CREDIT, KRW_50K));
    }

    @Test
    @DisplayName("a balanced manual entry posts via the guarded path with the operator as actor + dedupe row")
    void balancedPosts() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(CASH, TENANT)).thenReturn(true);
        when(ledgerAccountRepository.existsByCode(WALLET, TENANT)).thenReturn(true);
        when(clock.now()).thenReturn(NOW);
        when(postJournalEntryUseCase.post(any(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        Result result = useCase.post(cmd(balancedLines()));

        assertThat(result.replayed()).isFalse();
        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(postJournalEntryUseCase).post(entry.capture(), anyString(), org.mockito.ArgumentMatchers.eq(OPERATOR));
        JournalEntry posted = entry.getValue();
        assertThat(posted.isBalanced()).isTrue();
        assertThat(posted.source().getSourceType()).isEqualTo(SourceRef.TYPE_MANUAL);
        assertThat(posted.source().getSourceEventId()).isEqualTo(DEDUPE);
        assertThat(posted.source().getSourceTransactionId()).isEqualTo(KEY); // reference present
        verify(processedEventStore).markProcessed(DEDUPE, TENANT, "manual-posting", KEY, NOW);
    }

    @Test
    @DisplayName("an unbalanced manual entry → LEDGER_ENTRY_UNBALANCED, no post, no dedupe row")
    void unbalancedRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(CASH, TENANT)).thenReturn(true);
        when(ledgerAccountRepository.existsByCode(WALLET, TENANT)).thenReturn(true);

        List<ManualLine> unbalanced = List.of(
                line(CASH, EntryDirection.DEBIT, Money.of(50_000L, Currency.KRW)),
                line(WALLET, EntryDirection.CREDIT, Money.of(40_000L, Currency.KRW)));

        assertThatThrownBy(() -> useCase.post(cmd(unbalanced)))
                .isInstanceOf(LedgerEntryUnbalancedException.class);

        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("an unknown ledger account → LEDGER_ACCOUNT_NOT_FOUND, no post, no lazy mint")
    void unknownAccountRejected() {
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(false);
        when(ledgerAccountRepository.existsByCode(CASH, TENANT)).thenReturn(false);

        assertThatThrownBy(() -> useCase.post(cmd(balancedLines())))
                .isInstanceOf(LedgerAccountNotFoundException.class);

        verify(ledgerAccountRepository, never()).save(any());
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a replay (key already processed) returns the original entry — no second post")
    void replayReturnsOriginal() {
        JournalEntry original = JournalEntry.post("e-1", TENANT, NOW,
                SourceRef.ofManual(KEY, DEDUPE), balancedDomainLines());
        when(processedEventStore.isProcessed(DEDUPE)).thenReturn(true);
        when(journalRepository.findBySourceEventId(DEDUPE, TENANT))
                .thenReturn(Optional.of(original));

        Result result = useCase.post(cmd(balancedLines()));

        assertThat(result.replayed()).isTrue();
        assertThat(result.entry()).isSameAs(original);
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
        verify(processedEventStore, never()).markProcessed(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a blank Idempotency-Key → IDEMPOTENCY_KEY_REQUIRED before any work")
    void blankKeyRejected() {
        PostManualJournalEntryCommand blank = new PostManualJournalEntryCommand(
                TENANT, OPERATOR, "  ", NOW, KEY, "memo", balancedLines());

        assertThatThrownBy(() -> useCase.post(blank))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        verify(processedEventStore, never()).isProcessed(anyString());
        verify(postJournalEntryUseCase, never()).post(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("an oversized Idempotency-Key → IDEMPOTENCY_KEY_REQUIRED (key must fit the column)")
    void oversizedKeyRejected() {
        String tooLong = "k".repeat(51);
        PostManualJournalEntryCommand cmd = new PostManualJournalEntryCommand(
                TENANT, OPERATOR, tooLong, NOW, KEY, "memo", balancedLines());

        assertThatThrownBy(() -> useCase.post(cmd))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        verify(processedEventStore, never()).isProcessed(anyString());
    }

    private static List<JournalLine> balancedDomainLines() {
        return List.of(
                JournalLine.debit(TENANT, CASH, KRW_50K),
                JournalLine.credit(TENANT, WALLET, KRW_50K));
    }
}
