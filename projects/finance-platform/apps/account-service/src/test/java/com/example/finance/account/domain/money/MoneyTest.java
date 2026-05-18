package com.example.finance.account.domain.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit tests for {@link Money} (fintech F5 — integer minor units,
 * never float/double; currency mismatch guard; scale per currency).
 */
class MoneyTest {

    @Test
    @DisplayName("F5: Money carries an integer minor-unit count + currency")
    void minorUnitsConstruction() {
        Money m = Money.of(150_000L, Currency.KRW);
        assertThat(m.minorUnits()).isEqualTo(150_000L);
        assertThat(m.currency()).isEqualTo(Currency.KRW);
        assertThat(m.toMinorString()).isEqualTo("150000");
    }

    @Test
    @DisplayName("F5: string-encoded minor units parse to the same value")
    void stringMinorUnits() {
        assertThat(Money.of("1000", Currency.USD).minorUnits()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("currency minor-unit scale: KRW=0, USD=2, EUR=2, JPY=0")
    void currencyScale() {
        assertThat(Currency.KRW.minorUnitScale()).isZero();
        assertThat(Currency.USD.minorUnitScale()).isEqualTo(2);
        assertThat(Currency.EUR.minorUnitScale()).isEqualTo(2);
        assertThat(Currency.JPY.minorUnitScale()).isZero();
    }

    @Test
    @DisplayName("negative minor units rejected at construction")
    void rejectNegative() {
        assertThatThrownBy(() -> Money.of(-1L, Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1,000", "abc", "", "10.00"})
    @DisplayName("F5: non-integer (float/decimal) string amount rejected")
    void rejectNonIntegerString(String raw) {
        assertThatThrownBy(() -> Money.of(raw, Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("integer minor-unit string");
    }

    @Test
    @DisplayName("add/subtract are exact integer arithmetic")
    void arithmetic() {
        Money a = Money.of(1000L, Currency.USD);
        Money b = Money.of(250L, Currency.USD);
        assertThat(a.add(b).minorUnits()).isEqualTo(1250L);
        assertThat(a.subtract(b).minorUnits()).isEqualTo(750L);
    }

    @Test
    @DisplayName("subtract that would go negative is rejected")
    void subtractNegativeRejected() {
        Money a = Money.of(100L, Currency.USD);
        Money b = Money.of(101L, Currency.USD);
        assertThatThrownBy(() -> a.subtract(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("F5: mixed-currency add → CurrencyMismatchException")
    void currencyMismatchAdd() {
        Money usd = Money.of(100L, Currency.USD);
        Money krw = Money.of(100L, Currency.KRW);
        assertThatThrownBy(() -> usd.add(krw))
                .isInstanceOf(Money.CurrencyMismatchException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("KRW");
    }

    @Test
    @DisplayName("F5: mixed-currency compare → CurrencyMismatchException")
    void currencyMismatchCompare() {
        Money usd = Money.of(100L, Currency.USD);
        Money eur = Money.of(100L, Currency.EUR);
        assertThatThrownBy(() -> usd.isGreaterThan(eur))
                .isInstanceOf(Money.CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("comparisons within a currency")
    void comparisons() {
        Money small = Money.of(100L, Currency.USD);
        Money big = Money.of(200L, Currency.USD);
        assertThat(big.isGreaterThan(small)).isTrue();
        assertThat(small.isLessThan(big)).isTrue();
        assertThat(big.isGreaterThanOrEqual(Money.of(200L, Currency.USD))).isTrue();
    }

    @Test
    @DisplayName("Currency.of rejects unknown / wrong-length codes")
    void unsupportedCurrency() {
        assertThatThrownBy(() -> Currency.of("XYZ"))
                .isInstanceOf(Currency.UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> Currency.of("US"))
                .isInstanceOf(Currency.UnsupportedCurrencyException.class);
    }

    @Test
    @DisplayName("Currency.of normalises case")
    void currencyCaseInsensitive() {
        assertThat(Currency.of("usd")).isEqualTo(Currency.USD);
    }

    @Test
    @DisplayName("zero + equality")
    void zeroAndEquality() {
        assertThat(Money.zero(Currency.KRW).isZero()).isTrue();
        assertThat(Money.of(5L, Currency.USD)).isEqualTo(Money.of(5L, Currency.USD));
        assertThat(Money.of(5L, Currency.USD)).isNotEqualTo(Money.of(5L, Currency.KRW));
    }
}
