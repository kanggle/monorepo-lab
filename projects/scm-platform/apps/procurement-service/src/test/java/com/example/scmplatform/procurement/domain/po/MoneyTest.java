package com.example.scmplatform.procurement.domain.po;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit tests for {@link Money} value object.
 */
class MoneyTest {

    @Test
    @DisplayName("Money.of stores amount + uppercase currency code")
    void factoryNormalisesCurrencyCase() {
        Money money = Money.of(new BigDecimal("100.00"), "usd");
        assertThat(money.getAmount()).isEqualByComparingTo("100.00");
        assertThat(money.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Money.zero(currency) returns 0 amount")
    void zeroFactory() {
        Money money = Money.zero("EUR");
        assertThat(money.getAmount()).isEqualByComparingTo("0");
        assertThat(money.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Money.of rejects null amount")
    void rejectsNullAmount() {
        assertThatThrownBy(() -> Money.of(null, "USD"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Money.of rejects null currency")
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("currency");
    }

    @Test
    @DisplayName("Money.of rejects negative amount")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01"), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "USDD", "U", "usdollar", ""})
    @DisplayName("Money.of rejects non-3-letter currency codes")
    void rejectsInvalidCurrencyLength(String code) {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3-letter");
    }

    @Test
    @DisplayName("add() sums amounts when currencies match")
    void addSameCurrency() {
        Money a = Money.of(new BigDecimal("10.50"), "USD");
        Money b = Money.of(new BigDecimal("5.25"), "USD");
        Money sum = a.add(b);
        assertThat(sum.getAmount()).isEqualByComparingTo("15.75");
        assertThat(sum.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("add() throws on currency mismatch")
    void addCurrencyMismatch() {
        Money usd = Money.of(BigDecimal.ONE, "USD");
        Money eur = Money.of(BigDecimal.ONE, "EUR");
        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch")
                .hasMessageContaining("USD")
                .hasMessageContaining("EUR");
    }

    @Test
    @DisplayName("Zero amount is allowed")
    void zeroAmountAllowed() {
        Money money = Money.of(BigDecimal.ZERO, "USD");
        assertThat(money.getAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Adding zero returns same value")
    void addZero() {
        Money a = Money.of(new BigDecimal("100.00"), "USD");
        Money zero = Money.zero("USD");
        Money sum = a.add(zero);
        assertThat(sum.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Currency code lowercased input is uppercased on storage")
    void currencyLowercaseAccepted() {
        Money money = Money.of(BigDecimal.ONE, "krw");
        assertThat(money.getCurrency()).isEqualTo("KRW");
    }
}
