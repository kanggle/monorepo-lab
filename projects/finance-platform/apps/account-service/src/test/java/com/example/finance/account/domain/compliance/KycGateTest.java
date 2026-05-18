package com.example.finance.account.domain.compliance;

import com.example.finance.account.domain.account.KycLevel;
import com.example.finance.account.domain.error.DomainErrors.KycRequiredException;
import com.example.finance.account.domain.error.DomainErrors.TransactionLimitExceededException;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit tests for {@link KycGate} (fintech F4 — KYC-level fund ceiling
 * is the precondition of fund movement).
 */
class KycGateTest {

    private static Money krw(long m) {
        return Money.of(m, Currency.KRW);
    }

    @Test
    @DisplayName("F4: NONE level cannot move funds → KYC_REQUIRED")
    void noneBlocked() {
        assertThatThrownBy(() -> KycGate.ensurePermitted(
                KycLevel.NONE, TransactionType.HOLD, krw(1L)))
                .isInstanceOf(KycRequiredException.class);
    }

    @Test
    @DisplayName("F4: NONE may still RELEASE (returning held funds is never blocked)")
    void noneCanRelease() {
        assertThatCode(() -> KycGate.ensurePermitted(
                KycLevel.NONE, TransactionType.RELEASE, krw(999_999_999L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("F4: BASIC within ceiling permitted, over ceiling rejected")
    void basicCeiling() {
        assertThatCode(() -> KycGate.ensurePermitted(
                KycLevel.BASIC, TransactionType.HOLD,
                krw(KycGate.BASIC_LIMIT_MINOR)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> KycGate.ensurePermitted(
                KycLevel.BASIC, TransactionType.HOLD,
                krw(KycGate.BASIC_LIMIT_MINOR + 1)))
                .isInstanceOf(TransactionLimitExceededException.class);
    }

    @Test
    @DisplayName("F4: FULL within standard ceiling permitted")
    void fullCeiling() {
        assertThatCode(() -> KycGate.ensurePermitted(
                KycLevel.FULL, TransactionType.TRANSFER,
                krw(KycGate.FULL_LIMIT_MINOR)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> KycGate.ensurePermitted(
                KycLevel.FULL, TransactionType.TRANSFER,
                krw(KycGate.FULL_LIMIT_MINOR + 1)))
                .isInstanceOf(TransactionLimitExceededException.class);
    }
}
