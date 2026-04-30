package com.example.membership.domain.subscription.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubscriptionStatusMachine")
class SubscriptionStatusMachineTest {

    private final SubscriptionStatusMachine machine = new SubscriptionStatusMachine();

    @Nested
    @DisplayName("allowed transitions")
    class Allowed {

        @Test
        @DisplayName("NONE -> ACTIVE is allowed (activate)")
        void noneToActive() {
            assertThatCode(() -> machine.transition(SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE))
                    .doesNotThrowAnyException();
            assertThat(machine.isAllowed(SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE -> EXPIRED is allowed (scheduler)")
        void activeToExpired() {
            assertThatCode(() -> machine.transition(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ACTIVE -> CANCELLED is allowed (user cancel)")
        void activeToCancelled() {
            assertThatCode(() -> machine.transition(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("forbidden transitions")
    class Forbidden {

        @Test
        @DisplayName("EXPIRED -> ACTIVE is forbidden (renewals must create new subscription)")
        void expiredToActive() {
            assertThatThrownBy(() -> machine.transition(SubscriptionStatus.EXPIRED, SubscriptionStatus.ACTIVE))
                    .isInstanceOf(SubscriptionStateTransitionException.class);
        }

        @Test
        @DisplayName("CANCELLED -> ACTIVE is forbidden")
        void cancelledToActive() {
            assertThatThrownBy(() -> machine.transition(SubscriptionStatus.CANCELLED, SubscriptionStatus.ACTIVE))
                    .isInstanceOf(SubscriptionStateTransitionException.class);
        }

        @Test
        @DisplayName("NONE -> EXPIRED is forbidden")
        void noneToExpired() {
            assertThatThrownBy(() -> machine.transition(SubscriptionStatus.NONE, SubscriptionStatus.EXPIRED))
                    .isInstanceOf(SubscriptionStateTransitionException.class);
        }

        @Test
        @DisplayName("ACTIVE -> ACTIVE (same state) is forbidden")
        void activeToActive() {
            assertThatThrownBy(() -> machine.transition(SubscriptionStatus.ACTIVE, SubscriptionStatus.ACTIVE))
                    .isInstanceOf(SubscriptionStateTransitionException.class);
        }

        @Test
        @DisplayName("EXPIRED -> CANCELLED is forbidden")
        void expiredToCancelled() {
            assertThatThrownBy(() -> machine.transition(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED))
                    .isInstanceOf(SubscriptionStateTransitionException.class);
        }
    }
}
