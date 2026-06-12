package com.example.finance.ledger.domain.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("Money carries integer minor units — no float in the type at all")
    void minorUnitsOnly() {
        Money m = Money.of(150_000L, Currency.KRW);
        assertThat(m.minorUnits()).isEqualTo(150_000L);
        assertThat(m.toMinorString()).isEqualTo("150000");
        assertThat(m.currency()).isEqualTo(Currency.KRW);
    }

    @Test
    @DisplayName("KRW has scale 0, USD scale 2 (minor-unit interpretation, F5)")
    void currencyScale() {
        assertThat(Currency.KRW.minorUnitScale()).isZero();
        assertThat(Currency.USD.minorUnitScale()).isEqualTo(2);
    }

    @Test
    @DisplayName("negative amount is rejected at construction")
    void negativeRejected() {
        assertThatThrownBy(() -> Money.of(-1L, Currency.KRW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("string-encoded wire form parses to the same minor units")
    void stringForm() {
        assertThat(Money.of("150000", Currency.KRW)).isEqualTo(Money.of(150_000L, Currency.KRW));
        assertThatThrownBy(() -> Money.of("1.5", Currency.KRW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("currency-mismatched arithmetic raises CurrencyMismatchException")
    void currencyMismatch() {
        Money krw = Money.of(100L, Currency.KRW);
        Money usd = Money.of(100L, Currency.USD);
        assertThatThrownBy(() -> krw.add(usd))
                .isInstanceOf(Money.CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("add / subtract / absoluteDifference compute on minor units")
    void arithmetic() {
        Money a = Money.of(150_000L, Currency.KRW);
        Money b = Money.of(50_000L, Currency.KRW);
        assertThat(a.add(b)).isEqualTo(Money.of(200_000L, Currency.KRW));
        assertThat(a.subtract(b)).isEqualTo(Money.of(100_000L, Currency.KRW));
        assertThat(b.absoluteDifference(a)).isEqualTo(Money.of(100_000L, Currency.KRW));
    }

    @Test
    @DisplayName("unsupported currency code → UnsupportedCurrencyException")
    void unsupportedCurrency() {
        assertThatThrownBy(() -> Currency.of("XBT"))
                .isInstanceOf(Currency.UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> Currency.of("KR"))
                .isInstanceOf(Currency.UnsupportedCurrencyException.class);
    }
}
