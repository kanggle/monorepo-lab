package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingPolicyTest {

    private static final String TENANT = "finance";
    private static final Money MONEY = Money.of(150_000L, Currency.KRW);
    private static final String WALLET_A = LedgerAccountCodes.customerWallet("acc-A");
    private static final String WALLET_B = LedgerAccountCodes.customerWallet("acc-B");

    private static SourceRef source() {
        return SourceRef.ofTransaction("txn-1", "evt-1");
    }

    private static CompletedTransaction txn(LedgerTransactionType type, String counterparty) {
        return new CompletedTransaction(TENANT, "txn-1", "acc-A", type, MONEY, counterparty);
    }

    private static JournalLine line(JournalEntry e, String code) {
        return e.lines().stream().filter(l -> l.ledgerAccountCode().equals(code))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("TOPUP → DR CASH_CLEARING / CR CUSTOMER_WALLET:{acct}")
    void topup() {
        JournalEntry e = PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.TOPUP, null)).orElseThrow();
        assertThat(line(e, LedgerAccountCodes.CASH_CLEARING).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(line(e, WALLET_A).direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(e.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("WITHDRAW → DR CUSTOMER_WALLET:{acct} / CR CASH_CLEARING (reverse of TOPUP)")
    void withdraw() {
        JournalEntry e = PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.WITHDRAW, null)).orElseThrow();
        assertThat(line(e, WALLET_A).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(line(e, LedgerAccountCodes.CASH_CLEARING).direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(e.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("CAPTURE → DR CUSTOMER_WALLET:{acct} / CR SETTLEMENT_SUSPENSE")
    void capture() {
        JournalEntry e = PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.CAPTURE, null)).orElseThrow();
        assertThat(line(e, WALLET_A).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(line(e, LedgerAccountCodes.SETTLEMENT_SUSPENSE).direction())
                .isEqualTo(EntryDirection.CREDIT);
        assertThat(e.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("TRANSFER → DR CUSTOMER_WALLET:{from} / CR CUSTOMER_WALLET:{to} (two wallet lines)")
    void transfer() {
        JournalEntry e = PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.TRANSFER, "acc-B")).orElseThrow();
        assertThat(line(e, WALLET_A).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(line(e, WALLET_B).direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(e.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("TRANSFER without a counterparty → rejected")
    void transferMissingCounterparty() {
        assertThatThrownBy(() -> PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.TRANSFER, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HOLD → no entry (held/available is single-entry, account-service)")
    void holdNoEntry() {
        Optional<JournalEntry> e = PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.HOLD, null));
        assertThat(e).isEmpty();
    }

    @Test
    @DisplayName("RELEASE → no entry")
    void releaseNoEntry() {
        Optional<JournalEntry> e = PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.RELEASE, null));
        assertThat(e).isEmpty();
    }

    @Test
    @DisplayName("REVERSAL is not produced by the forward policy (it goes via reversed.v1)")
    void reversalNotForward() {
        assertThatThrownBy(() -> PostingPolicy.toEntry("e-1", Instant.now(), source(),
                txn(LedgerTransactionType.REVERSAL, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
