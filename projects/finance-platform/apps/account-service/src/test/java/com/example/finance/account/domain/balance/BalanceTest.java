package com.example.finance.account.domain.balance;

import com.example.finance.account.domain.error.DomainErrors.InsufficientAvailableBalanceException;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit tests for the {@link Balance} F2 invariant: available =
 * ledger − held, never negative; hold / capture / partial / release math.
 */
class BalanceTest {

    private static final Instant NOW = Instant.parse("2026-05-18T00:00:00Z");

    private Balance funded(long ledger) {
        Balance b = Balance.open("bal-1", "acc-1", "finance", Currency.KRW, NOW);
        if (ledger > 0) {
            b.credit(Money.of(ledger, Currency.KRW), NOW);
        }
        return b;
    }

    @Test
    @DisplayName("F2: available = ledger − held")
    void availableInvariant() {
        Balance b = funded(1000L);
        b.placeHold(Money.of(300L, Currency.KRW), NOW);
        assertThat(b.ledger().minorUnits()).isEqualTo(1000L);
        assertThat(b.held().minorUnits()).isEqualTo(300L);
        assertThat(b.available().minorUnits()).isEqualTo(700L);
    }

    @Test
    @DisplayName("F2: hold exceeding available is rejected (available never negative)")
    void holdExceedingAvailable() {
        Balance b = funded(500L);
        assertThatThrownBy(() -> b.placeHold(Money.of(501L, Currency.KRW), NOW))
                .isInstanceOf(InsufficientAvailableBalanceException.class);
        assertThat(b.held().minorUnits()).isZero();
    }

    @Test
    @DisplayName("full capture: ledger −= captured; held −= hold; available consistent")
    void fullCapture() {
        Balance b = funded(1000L);
        Money hold = Money.of(400L, Currency.KRW);
        b.placeHold(hold, NOW);
        b.captureHold(hold, hold, NOW);
        assertThat(b.ledger().minorUnits()).isEqualTo(600L);
        assertThat(b.held().minorUnits()).isZero();
        assertThat(b.available().minorUnits()).isEqualTo(600L);
    }

    @Test
    @DisplayName("partial capture: remainder released back to available")
    void partialCapture() {
        Balance b = funded(1000L);
        Money hold = Money.of(400L, Currency.KRW);
        Money captured = Money.of(250L, Currency.KRW);
        b.placeHold(hold, NOW);
        b.captureHold(hold, captured, NOW);
        // ledger 1000 − 250 = 750; held 400 − 400 = 0 → available 750
        assertThat(b.ledger().minorUnits()).isEqualTo(750L);
        assertThat(b.held().minorUnits()).isZero();
        assertThat(b.available().minorUnits()).isEqualTo(750L);
    }

    @Test
    @DisplayName("release returns held funds to available; ledger unchanged")
    void release() {
        Balance b = funded(1000L);
        Money hold = Money.of(400L, Currency.KRW);
        b.placeHold(hold, NOW);
        b.releaseHold(hold, NOW);
        assertThat(b.ledger().minorUnits()).isEqualTo(1000L);
        assertThat(b.held().minorUnits()).isZero();
        assertThat(b.available().minorUnits()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("debit guarded by available — cannot drive available negative")
    void debitGuard() {
        Balance b = funded(500L);
        b.placeHold(Money.of(300L, Currency.KRW), NOW); // available now 200
        assertThatThrownBy(() -> b.debit(Money.of(300L, Currency.KRW), NOW))
                .isInstanceOf(InsufficientAvailableBalanceException.class);
        b.debit(Money.of(200L, Currency.KRW), NOW); // ok: available exactly 200
        assertThat(b.ledger().minorUnits()).isEqualTo(300L);
    }

    @Test
    @DisplayName("F2: mixed-currency op rejected")
    void currencyMismatch() {
        Balance b = funded(1000L);
        assertThatThrownBy(() -> b.placeHold(Money.of(10L, Currency.USD), NOW))
                .isInstanceOf(com.example.finance.account.domain.error
                        .DomainErrors.CurrencyMismatchException.class);
    }
}
