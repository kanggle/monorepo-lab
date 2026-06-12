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
}
