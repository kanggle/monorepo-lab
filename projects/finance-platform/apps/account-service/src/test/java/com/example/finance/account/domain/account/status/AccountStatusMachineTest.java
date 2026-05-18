package com.example.finance.account.domain.account.status;

import com.example.finance.account.domain.error.DomainErrors.AccountStatusTransitionInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full transition matrix for {@link AccountStatusMachine} (architecture.md §
 * Account State Machine) — including forbidden transitions, self-transitions,
 * and the CLOSED terminal.
 */
class AccountStatusMachineTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_KYC,ACTIVE",
            "PENDING_KYC,CLOSED",
            "ACTIVE,RESTRICTED",
            "ACTIVE,FROZEN",
            "ACTIVE,CLOSED",
            "RESTRICTED,ACTIVE",
            "RESTRICTED,FROZEN",
            "RESTRICTED,CLOSED",
            "FROZEN,ACTIVE",
            "FROZEN,CLOSED"
    })
    @DisplayName("allowed transitions pass")
    void allowed(AccountStatus from, AccountStatus to) {
        assertThatCode(() -> AccountStatusMachine.ensureTransitionAllowed(from, to))
                .doesNotThrowAnyException();
        assertThat(AccountStatusMachine.isTransitionAllowed(from, to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING_KYC,RESTRICTED",
            "PENDING_KYC,FROZEN",
            "ACTIVE,PENDING_KYC",
            "RESTRICTED,PENDING_KYC",
            "FROZEN,RESTRICTED",
            "FROZEN,PENDING_KYC",
            "CLOSED,ACTIVE",
            "CLOSED,PENDING_KYC",
            "CLOSED,FROZEN"
    })
    @DisplayName("forbidden transitions throw")
    void forbidden(AccountStatus from, AccountStatus to) {
        assertThatThrownBy(() -> AccountStatusMachine.ensureTransitionAllowed(from, to))
                .isInstanceOf(AccountStatusTransitionInvalidException.class);
        assertThat(AccountStatusMachine.isTransitionAllowed(from, to)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PENDING_KYC", "ACTIVE", "RESTRICTED", "FROZEN", "CLOSED"})
    @DisplayName("self-transitions are forbidden")
    void selfTransitionForbidden(AccountStatus s) {
        assertThatThrownBy(() -> AccountStatusMachine.ensureTransitionAllowed(s, s))
                .isInstanceOf(AccountStatusTransitionInvalidException.class);
    }

    @Test
    @DisplayName("CLOSED is terminal — no outbound transition")
    void closedTerminal() {
        for (AccountStatus to : AccountStatus.values()) {
            assertThat(AccountStatusMachine.isTransitionAllowed(AccountStatus.CLOSED, to))
                    .isFalse();
        }
    }
}
