package com.example.finance.account.domain.transaction.status;

import com.example.finance.account.domain.error.DomainErrors.TransactionStatusTransitionInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full transition matrix for {@link TransactionStatusMachine} (architecture.md
 * § Transaction State Machine, fintech F3 — SETTLED/COMPLETED immutable).
 */
class TransactionStatusMachineTest {

    @ParameterizedTest
    @CsvSource({
            "REQUESTED,VALIDATED",
            "REQUESTED,FAILED",
            "VALIDATED,AUTHORIZED",
            "VALIDATED,FAILED",
            "AUTHORIZED,SETTLED",
            "AUTHORIZED,FAILED",
            "SETTLED,COMPLETED",
            "COMPLETED,REVERSED"
    })
    @DisplayName("allowed transitions pass")
    void allowed(TransactionStatus from, TransactionStatus to) {
        assertThatCode(() -> TransactionStatusMachine.ensureTransitionAllowed(from, to))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "REQUESTED,AUTHORIZED",
            "REQUESTED,SETTLED",
            "VALIDATED,SETTLED",
            "AUTHORIZED,COMPLETED",
            "SETTLED,FAILED",
            "SETTLED,REVERSED",
            "COMPLETED,SETTLED",
            "FAILED,REQUESTED",
            "REVERSED,COMPLETED"
    })
    @DisplayName("forbidden transitions throw (incl. post-SETTLED mutation, F3)")
    void forbidden(TransactionStatus from, TransactionStatus to) {
        assertThatThrownBy(() -> TransactionStatusMachine.ensureTransitionAllowed(from, to))
                .isInstanceOf(TransactionStatusTransitionInvalidException.class);
    }

    @ParameterizedTest
    @CsvSource({"REQUESTED", "VALIDATED", "AUTHORIZED", "SETTLED",
            "COMPLETED", "FAILED", "REVERSED"})
    @DisplayName("self-transitions forbidden")
    void selfForbidden(TransactionStatus s) {
        assertThatThrownBy(() -> TransactionStatusMachine.ensureTransitionAllowed(s, s))
                .isInstanceOf(TransactionStatusTransitionInvalidException.class);
    }

    @Test
    @DisplayName("F3: SETTLED/COMPLETED/REVERSED report immutable; FAILED/REVERSED terminal")
    void immutabilityFlags() {
        assertThat(TransactionStatus.SETTLED.isImmutable()).isTrue();
        assertThat(TransactionStatus.COMPLETED.isImmutable()).isTrue();
        assertThat(TransactionStatus.REVERSED.isImmutable()).isTrue();
        assertThat(TransactionStatus.AUTHORIZED.isImmutable()).isFalse();
        assertThat(TransactionStatus.FAILED.isTerminal()).isTrue();
        assertThat(TransactionStatus.REVERSED.isTerminal()).isTrue();
        assertThat(TransactionStatus.COMPLETED.isTerminal()).isTrue();
    }
}
