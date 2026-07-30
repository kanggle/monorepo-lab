package com.example.finance.common.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Currency#ofOrThrow(String, java.util.function.Function)}.
 *
 * <p>TASK-FIN-BE-064: {@code ofOrThrow} arrived with TASK-FIN-BE-061 and had
 * <b>no direct unit test</b> — only indirect coverage through three
 * ledger-service call sites ({@code GetFxRateOverrideUseCase},
 * {@code SetFxRateOverrideUseCase}, {@code SettlementController}). Now that the
 * method is shared by two services, the contract it promises — remap the
 * unsupported-code failure, but leave {@code null} alone — is asserted directly.
 */
class CurrencyTest {

    /** Stand-in for a call site's own contract error type (e.g. VALIDATION_ERROR). */
    private static final class CallerChosenException extends RuntimeException {
        CallerChosenException(String message) {
            super(message);
        }
    }

    @Test
    @DisplayName("ofOrThrow returns the currency and never calls the factory for a supported code")
    void supportedCodePassesThrough() {
        assertThat(Currency.ofOrThrow("usd", code -> {
            throw new AssertionError("factory must not be invoked for a supported code: " + code);
        })).isEqualTo(Currency.USD);
    }

    @Test
    @DisplayName("ofOrThrow remaps UnsupportedCurrencyException to the caller's exception, with the offending code")
    void unsupportedCodeRemapped() {
        assertThatThrownBy(() -> Currency.ofOrThrow("XBT",
                code -> new CallerChosenException("rejected: " + code)))
                .isInstanceOf(CallerChosenException.class)
                .hasMessageContaining("XBT");
    }

    @Test
    @DisplayName("ofOrThrow remaps a wrong-length code too (same guard, same remap)")
    void wrongLengthCodeRemapped() {
        assertThatThrownBy(() -> Currency.ofOrThrow("KR",
                code -> new CallerChosenException("rejected: " + code)))
                .isInstanceOf(CallerChosenException.class)
                .hasMessageContaining("KR");
    }

    @Test
    @DisplayName("ofOrThrow does NOT remap null — NullPointerException still surfaces (absent != unsupported)")
    void nullCodeNotRemapped() {
        assertThatThrownBy(() -> Currency.ofOrThrow(null,
                code -> new CallerChosenException("rejected: " + code)))
                .isInstanceOf(NullPointerException.class);
    }
}
