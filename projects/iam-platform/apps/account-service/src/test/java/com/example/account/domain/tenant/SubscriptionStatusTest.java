package com.example.account.domain.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-341 (ADR-MONO-023 D1): unit coverage for the subscription lifecycle
 * state machine guard.
 */
@DisplayName("SubscriptionStatus — lifecycle state machine (ADR-023 D1)")
class SubscriptionStatusTest {

    @Nested
    @DisplayName("isActive / isTerminal")
    class StatePredicates {

        @Test
        @DisplayName("only ACTIVE is active")
        void onlyActiveIsActive() {
            assertThat(SubscriptionStatus.ACTIVE.isActive()).isTrue();
            assertThat(SubscriptionStatus.PENDING.isActive()).isFalse();
            assertThat(SubscriptionStatus.SUSPENDED.isActive()).isFalse();
            assertThat(SubscriptionStatus.CANCELLED.isActive()).isFalse();
        }

        @Test
        @DisplayName("only CANCELLED is terminal")
        void onlyCancelledIsTerminal() {
            assertThat(SubscriptionStatus.CANCELLED.isTerminal()).isTrue();
            assertThat(SubscriptionStatus.ACTIVE.isTerminal()).isFalse();
            assertThat(SubscriptionStatus.PENDING.isTerminal()).isFalse();
            assertThat(SubscriptionStatus.SUSPENDED.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("canTransitionTo — legal transitions")
    class LegalTransitions {

        @ParameterizedTest(name = "{0} → {1} is legal")
        @CsvSource({
                "PENDING,ACTIVE",
                "PENDING,CANCELLED",
                "ACTIVE,SUSPENDED",
                "ACTIVE,CANCELLED",
                "SUSPENDED,ACTIVE",
                "SUSPENDED,CANCELLED",
        })
        void legalTransitionsAllowed(SubscriptionStatus from, SubscriptionStatus to) {
            assertThat(from.canTransitionTo(to)).isTrue();
        }
    }

    @Nested
    @DisplayName("canTransitionTo — illegal transitions")
    class IllegalTransitions {

        @ParameterizedTest(name = "{0} → {1} is illegal")
        @CsvSource({
                // CANCELLED is terminal
                "CANCELLED,ACTIVE",
                "CANCELLED,SUSPENDED",
                "CANCELLED,PENDING",
                // cannot re-enter PENDING
                "ACTIVE,PENDING",
                "SUSPENDED,PENDING",
                // cannot jump SUSPENDED straight from PENDING (must activate first)
                "PENDING,SUSPENDED",
        })
        void illegalTransitionsRejected(SubscriptionStatus from, SubscriptionStatus to) {
            assertThat(from.canTransitionTo(to)).isFalse();
        }

        @ParameterizedTest(name = "{0} → {0} (self) is not a transition")
        @EnumSource(SubscriptionStatus.class)
        void selfTransitionRejected(SubscriptionStatus s) {
            assertThat(s.canTransitionTo(s))
                    .as("self-transition is an idempotent no-op, not a legal transition")
                    .isFalse();
        }

        @ParameterizedTest(name = "{0} → null is rejected")
        @EnumSource(SubscriptionStatus.class)
        void nullTargetRejected(SubscriptionStatus s) {
            assertThat(s.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    @DisplayName("creatable() = {PENDING, ACTIVE} — a new subscription never starts SUSPENDED/CANCELLED")
    void creatableStates() {
        assertThat(SubscriptionStatus.creatable())
                .containsExactlyInAnyOrder(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE);
    }
}
