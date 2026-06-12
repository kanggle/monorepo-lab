package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link JournalLine}'s multi-currency form (8th increment,
 * TASK-FIN-BE-014, AC-1/AC-4). The {@code of(money, baseAmount)} factory derives
 * the exact minor-to-minor {@code exchangeRate}; the base amount must be the base
 * (KRW) currency. Money stays integer minor units (F5) — only the rate is decimal.
 */
class JournalLineTest {

    private static final String TENANT = "finance";

    @Test
    @DisplayName("of(money, baseAmount) sets exchangeRate = base.minor / money.minor (scale 8)")
    void rateDerivedFromMinorUnits() {
        Money usd = Money.of(10_000L, Currency.USD);          // $100.00
        Money base = Money.of(135_000L, Currency.KRW);        // 135,000 KRW

        JournalLine line = JournalLine.of(TENANT, "CASH_CLEARING",
                EntryDirection.DEBIT, usd, base);

        assertThat(line.money()).isEqualTo(usd);
        assertThat(line.baseMoney()).isEqualTo(base);
        assertThat(line.baseCurrency()).isEqualTo(Currency.KRW);
        assertThat(line.baseAmountMinor()).isEqualTo(135_000L);
        // 135000 / 10000 = 13.5 (minor-to-minor provenance factor)
        assertThat(line.exchangeRate()).isEqualByComparingTo(new BigDecimal("13.5"));
    }

    @Test
    @DisplayName("a base-currency (KRW) line via the single-arg form: base == money, rate == 1")
    void singleArgFormBaseEqualsMoney() {
        Money krw = Money.of(150_000L, Currency.KRW);
        JournalLine line = JournalLine.debit(TENANT, "CASH_CLEARING", krw);

        assertThat(line.baseMoney()).isEqualTo(krw);
        assertThat(line.exchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(line.baseCurrency()).isEqualTo(Currency.KRW);
    }

    @Test
    @DisplayName("a non-KRW base amount → CurrencyMismatchException (base must be the reporting currency)")
    void nonBaseBaseAmountRejected() {
        Money usd = Money.of(10_000L, Currency.USD);
        Money badBase = Money.of(10_000L, Currency.USD); // not KRW

        assertThatThrownBy(() -> JournalLine.of(TENANT, "CASH_CLEARING",
                EntryDirection.DEBIT, usd, badBase))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("F5: the rate is a BigDecimal (no float); money stays integer minor units")
    void rateIsBigDecimalMoneyIsLong() {
        Money usd = Money.of(3L, Currency.USD);          // forces a repeating decimal
        Money base = Money.of(40L, Currency.KRW);

        JournalLine line = JournalLine.of(TENANT, "CASH_CLEARING",
                EntryDirection.DEBIT, usd, base);

        // money + base are exact integers; the rate is a scale-8 BigDecimal provenance
        assertThat(line.money().minorUnits()).isEqualTo(3L);
        assertThat(line.baseAmountMinor()).isEqualTo(40L);
        assertThat(line.exchangeRate().scale()).isEqualTo(8);
        // 40 / 3 = 13.33333333 (HALF_UP at scale 8) — never re-derives the balance
        assertThat(line.exchangeRate()).isEqualByComparingTo(new BigDecimal("13.33333333"));
    }

    // ---- (9th incr) baseAdjustment factory — FX revaluation, TASK-FIN-BE-015 ----

    @Test
    @DisplayName("(9th incr) baseAdjustment: zero foreign money, non-zero KRW base, rate = spot")
    void baseAdjustmentZeroForeignNonZeroBase() {
        Money baseDelta = Money.of(5_000L, Currency.KRW);
        JournalLine line = JournalLine.baseAdjustment(TENANT, "CASH_CLEARING", Currency.USD,
                EntryDirection.DEBIT, baseDelta, new BigDecimal("13.5"));

        assertThat(line.money().minorUnits()).isZero();              // foreign quantity unchanged
        assertThat(line.money().currency()).isEqualTo(Currency.USD);
        assertThat(line.baseMoney()).isEqualTo(baseDelta);
        assertThat(line.baseCurrency()).isEqualTo(Currency.KRW);
        assertThat(line.exchangeRate()).isEqualByComparingTo(new BigDecimal("13.5"));
        assertThat(line.isDebit()).isTrue();
    }

    @Test
    @DisplayName("(9th incr) an entry of [baseAdjustment, contra] balances in the base currency")
    void baseAdjustmentEntryBalancesInBase() {
        Money baseDelta = Money.of(5_000L, Currency.KRW);
        JournalLine adjustment = JournalLine.baseAdjustment(TENANT, "CASH_CLEARING", Currency.USD,
                EntryDirection.DEBIT, baseDelta, new BigDecimal("13.5"));
        JournalLine contra = JournalLine.credit(TENANT, "FX_GAIN", baseDelta);

        JournalEntry entry = JournalEntry.post("e-1", TENANT,
                java.time.Instant.parse("2026-06-30T23:59:59Z"),
                SourceRef.ofRevaluation("FX-REVAL", "reval:k-1"),
                java.util.List.of(adjustment, contra));

        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.baseDebitTotal().minorUnits()).isEqualTo(5_000L);
        assertThat(entry.baseCreditTotal().minorUnits()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("(9th incr) reversed() preserves a base-adjustment line (money/base/rate; direction flips)")
    void baseAdjustmentReversedPreserves() {
        Money baseDelta = Money.of(5_000L, Currency.KRW);
        JournalLine adjustment = JournalLine.baseAdjustment(TENANT, "CASH_CLEARING", Currency.USD,
                EntryDirection.DEBIT, baseDelta, new BigDecimal("13.5"));

        JournalLine reversed = adjustment.reversed();

        assertThat(reversed.isCredit()).isTrue();                   // direction flipped
        assertThat(reversed.money().minorUnits()).isZero();
        assertThat(reversed.money().currency()).isEqualTo(Currency.USD);
        assertThat(reversed.baseMoney()).isEqualTo(baseDelta);
        assertThat(reversed.exchangeRate()).isEqualByComparingTo(new BigDecimal("13.5"));
    }

    @Test
    @DisplayName("(9th incr) a non-KRW base adjustment amount → CurrencyMismatchException")
    void baseAdjustmentNonBaseBaseAmountRejected() {
        Money badBase = Money.of(5_000L, Currency.USD); // not KRW
        assertThatThrownBy(() -> JournalLine.baseAdjustment(TENANT, "CASH_CLEARING", Currency.USD,
                EntryDirection.DEBIT, badBase, new BigDecimal("13.5")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("(9th incr) a base-currency (KRW) position has no FX exposure → CurrencyMismatchException")
    void baseAdjustmentBaseCurrencyPositionRejected() {
        Money baseDelta = Money.of(5_000L, Currency.KRW);
        assertThatThrownBy(() -> JournalLine.baseAdjustment(TENANT, "CASH_CLEARING", Currency.KRW,
                EntryDirection.DEBIT, baseDelta, new BigDecimal("1")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("(9th incr) the positive of/debit/credit factories still reject a zero amount")
    void positiveFactoriesStillRejectZero() {
        Money zeroKrw = Money.of(0L, Currency.KRW);
        assertThatThrownBy(() -> JournalLine.debit(TENANT, "CASH_CLEARING", zeroKrw))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JournalLine.credit(TENANT, "FX_GAIN", zeroKrw))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JournalLine.of(TENANT, "CASH_CLEARING",
                EntryDirection.DEBIT, zeroKrw))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
