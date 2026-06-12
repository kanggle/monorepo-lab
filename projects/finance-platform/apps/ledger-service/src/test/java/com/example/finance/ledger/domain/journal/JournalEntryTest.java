package com.example.finance.ledger.domain.journal;

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
    @DisplayName("(8th incr) a cross-currency entry whose base amounts balance is accepted")
    void crossCurrencyBaseBalancedOk() {
        // DR $100.00 (10000 USD-minor → 135000 KRW) / CR 135000 KRW; base amounts net.
        Money usd = Money.of(10_000L, Currency.USD);
        Money baseKrw = Money.of(135_000L, Currency.KRW);
        JournalEntry entry = JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.of(TENANT, "CASH_CLEARING", EntryDirection.DEBIT, usd, baseKrw),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1", baseKrw)));

        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.baseDebitTotal()).isEqualTo(baseKrw);
        assertThat(entry.baseCreditTotal()).isEqualTo(baseKrw);
        // the USD line keeps its transaction money but records the KRW base value
        JournalLine cash = entry.lines().stream()
                .filter(l -> l.ledgerAccountCode().equals("CASH_CLEARING"))
                .findFirst().orElseThrow();
        assertThat(cash.money()).isEqualTo(usd);
        assertThat(cash.baseMoney()).isEqualTo(baseKrw);
        assertThat(cash.baseCurrency()).isEqualTo(Currency.KRW);
    }

    @Test
    @DisplayName("(8th incr) a cross-currency entry whose base amounts do NOT net → LEDGER_ENTRY_UNBALANCED")
    void crossCurrencyBaseUnbalancedRejected() {
        Money usd = Money.of(10_000L, Currency.USD);
        Money baseDr = Money.of(135_000L, Currency.KRW);
        Money baseCr = Money.of(130_000L, Currency.KRW);
        assertThatThrownBy(() -> JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.of(TENANT, "CASH_CLEARING", EntryDirection.DEBIT, usd, baseDr),
                JournalLine.of(TENANT, "CUSTOMER_WALLET:acc-1", EntryDirection.CREDIT,
                        Money.of(130_000L, Currency.KRW), baseCr))))
                .isInstanceOf(LedgerEntryUnbalancedException.class);
    }

    @Test
    @DisplayName("(8th incr) a single-currency KRW entry is unchanged — base == amount, rate == 1")
    void singleCurrencyBaseEqualsAmount() {
        JournalEntry entry = JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.debit(TENANT, "CASH_CLEARING", KRW_150K),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1", KRW_150K)));

        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.baseDebitTotal()).isEqualTo(KRW_150K);
        assertThat(entry.baseCreditTotal()).isEqualTo(KRW_150K);
        assertThat(entry.lines()).allSatisfy(l -> {
            assertThat(l.baseMoney()).isEqualTo(l.money());
            assertThat(l.exchangeRate()).isEqualByComparingTo(java.math.BigDecimal.ONE);
            assertThat(l.baseCurrency()).isEqualTo(Currency.KRW);
        });
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

    @Test
    @DisplayName("(8th incr) reversal of a multi-currency entry preserves money + rate + base, stays balanced")
    void reversalPreservesMultiCurrency() {
        Money usd = Money.of(10_000L, Currency.USD);
        Money baseKrw = Money.of(135_000L, Currency.KRW);
        JournalEntry original = JournalEntry.post("e-1", TENANT, Instant.now(), source(), List.of(
                JournalLine.of(TENANT, "CASH_CLEARING", EntryDirection.DEBIT, usd, baseKrw),
                JournalLine.credit(TENANT, "CUSTOMER_WALLET:acc-1", baseKrw)));

        JournalEntry reversal = JournalEntry.reversalEntry(
                "e-2", Instant.now(), SourceRef.ofTransaction("rev-txn", "rev-evt"), original);

        assertThat(reversal.isBalanced()).isTrue();
        JournalLine cash = reversal.lines().stream()
                .filter(l -> l.ledgerAccountCode().equals("CASH_CLEARING"))
                .findFirst().orElseThrow();
        // direction flipped, but money + rate + base are preserved
        assertThat(cash.direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(cash.money()).isEqualTo(usd);
        assertThat(cash.baseMoney()).isEqualTo(baseKrw);
        // rate = base.minor / money.minor = 135000 / 10000 = 13.5
        assertThat(cash.exchangeRate()).isEqualByComparingTo(new java.math.BigDecimal("13.5"));
    }
}
