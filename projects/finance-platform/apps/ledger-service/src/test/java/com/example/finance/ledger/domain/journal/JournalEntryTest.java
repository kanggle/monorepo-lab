package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerEntryUnbalancedException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    private static final String TENANT = "finance";
    private static final Money KRW_150K = Money.of(150_000L, Currency.KRW);

    private static SourceRef source() {
        return SourceRef.ofTransaction("txn-1", "evt-1");
    }

    @Test
    @DisplayName("a balanced 2-line entry is posted and reports balanced")
    void balancedOk() {
        JournalEntry entry = JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.debit(TENANT, "CASH_CLEARING", KRW_150K),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1", KRW_150K)));

        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.debitTotal()).isEqualTo(KRW_150K);
        assertThat(entry.creditTotal()).isEqualTo(KRW_150K);
        assertThat(entry.lines()).hasSize(2);
        assertThat(entry.isReversal()).isFalse();
        // lines were stamped with the entry id + posted instant
        assertThat(entry.lines()).allSatisfy(l -> assertThat(l.entryId()).isEqualTo("e-1"));
    }

    @Test
    @DisplayName("Σ debit != Σ credit → LedgerEntryUnbalancedException (self-validated in the factory)")
    void unbalancedRejected() {
        assertThatThrownBy(() -> JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.debit(TENANT, "CASH_CLEARING", KRW_150K),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1",
                        Money.of(100_000L, Currency.KRW)))))
                .isInstanceOf(LedgerEntryUnbalancedException.class)
                .hasMessageContaining("unbalanced");
    }

    @Test
    @DisplayName("fewer than two lines → LedgerEntryUnbalancedException")
    void tooFewLines() {
        assertThatThrownBy(() -> JournalEntry.post("e-1", TENANT, Instant.now(), source(),
                List.of(JournalLine.debit(TENANT, "CASH_CLEARING", KRW_150K))))
                .isInstanceOf(LedgerEntryUnbalancedException.class);
    }

    @Test
    @DisplayName("cross-currency lines in one entry → CurrencyMismatchException")
    void crossCurrencyRejected() {
        assertThatThrownBy(() -> JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.debit(TENANT, "CASH_CLEARING", KRW_150K),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1",
                        Money.of(150_000L, Currency.USD)))))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("posted entry is immutable — lines() returns an unmodifiable view")
    void immutableLines() {
        JournalEntry entry = JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.debit(TENANT, "CASH_CLEARING", KRW_150K),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1", KRW_150K)));
        assertThatThrownBy(() -> entry.lines().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("reversal entry swaps debit/credit, references the original, stays balanced (F3)")
    void reversalSwap() {
        JournalEntry original = JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.debit(TENANT, "CASH_CLEARING", KRW_150K),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1", KRW_150K)));

        JournalEntry reversal = JournalEntry.reversalEntry(
                "e-2", Instant.now(), SourceRef.ofTransaction("rev-txn", "rev-evt"), original);

        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.reversalOfEntryId()).isEqualTo("e-1");
        assertThat(reversal.isBalanced()).isTrue();
        // the CASH_CLEARING line flipped from DEBIT to CREDIT
        JournalLine cash = reversal.lines().stream()
                .filter(l -> l.ledgerAccountCode().equals("CASH_CLEARING"))
                .findFirst().orElseThrow();
        assertThat(cash.direction()).isEqualTo(EntryDirection.CREDIT);
        JournalLine wallet = reversal.lines().stream()
                .filter(l -> l.ledgerAccountCode().equals("CUSTOMER_WALLET:acc-1"))
                .findFirst().orElseThrow();
        assertThat(wallet.direction()).isEqualTo(EntryDirection.DEBIT);
    }
}
