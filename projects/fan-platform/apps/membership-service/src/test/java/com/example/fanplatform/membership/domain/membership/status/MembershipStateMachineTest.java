package com.example.fanplatform.membership.domain.membership.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipStateMachineTest {

    @Test
    @DisplayName("ACTIVE → CANCELED is allowed")
    void activeToCanceled() {
        assertThatCode(() -> MembershipStateMachine.ensureTransitionAllowed(
                MembershipStatus.ACTIVE, MembershipStatus.CANCELED))
                .doesNotThrowAnyException();
        assertThat(MembershipStateMachine.isTransitionAllowed(
                MembershipStatus.ACTIVE, MembershipStatus.CANCELED)).isTrue();
    }

    @Test
    @DisplayName("CANCELED → CANCELED is forbidden by the machine (the no-op is handled by the use case)")
    void canceledToCanceledForbidden() {
        assertThatThrownBy(() -> MembershipStateMachine.ensureTransitionAllowed(
                MembershipStatus.CANCELED, MembershipStatus.CANCELED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("CANCELED → ACTIVE is forbidden (CANCELED is terminal)")
    void canceledToActiveForbidden() {
        assertThatThrownBy(() -> MembershipStateMachine.ensureTransitionAllowed(
                MembershipStatus.CANCELED, MembershipStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(MembershipStateMachine.isTransitionAllowed(
                MembershipStatus.CANCELED, MembershipStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("ACTIVE → ACTIVE self-transition is forbidden")
    void activeToActiveForbidden() {
        assertThatThrownBy(() -> MembershipStateMachine.ensureTransitionAllowed(
                MembershipStatus.ACTIVE, MembershipStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("InvalidStateTransitionException carries from/to")
    void exceptionCarriesFromTo() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                MembershipStatus.CANCELED, MembershipStatus.ACTIVE);
        assertThat(ex.from()).isEqualTo(MembershipStatus.CANCELED);
        assertThat(ex.to()).isEqualTo(MembershipStatus.ACTIVE);
    }
}
