package com.example.scmplatform.procurement.domain.po.status;

import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit tests for {@link PoStatusMachine}. Asserts the full transition
 * matrix declared in TASK-SCM-BE-002 § Acceptance Criteria #9 plus the
 * terminal-state and self-transition guard rails.
 */
class PoStatusMachineTest {

    @Nested
    @DisplayName("Allowed transitions per actor")
    class AllowedTransitions {

        @Test
        @DisplayName("BUYER: DRAFT → SUBMITTED")
        void buyerDraftToSubmitted() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.DRAFT, PoStatus.SUBMITTED, ActorType.BUYER);
        }

        @ParameterizedTest
        @EnumSource(value = PoStatus.class, names = {"DRAFT", "SUBMITTED", "ACKNOWLEDGED"})
        @DisplayName("BUYER: cancel from DRAFT/SUBMITTED/ACKNOWLEDGED")
        void buyerCancelAllowed(PoStatus from) {
            PoStatusMachine.ensureTransitionAllowed(from, PoStatus.CANCELED, ActorType.BUYER);
        }

        @Test
        @DisplayName("SUPPLIER: SUBMITTED → ACKNOWLEDGED")
        void supplierAcknowledge() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.SUBMITTED, PoStatus.ACKNOWLEDGED, ActorType.SUPPLIER);
        }

        @Test
        @DisplayName("OPERATOR: ACKNOWLEDGED → CONFIRMED")
        void operatorConfirm() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.ACKNOWLEDGED, PoStatus.CONFIRMED, ActorType.OPERATOR);
        }

        @ParameterizedTest
        @EnumSource(value = PoStatus.class, names = {"DRAFT", "SUBMITTED", "ACKNOWLEDGED"})
        @DisplayName("OPERATOR: cancel from DRAFT/SUBMITTED/ACKNOWLEDGED")
        void operatorCancelAllowed(PoStatus from) {
            PoStatusMachine.ensureTransitionAllowed(from, PoStatus.CANCELED, ActorType.OPERATOR);
        }

        @Test
        @DisplayName("OPERATOR: DRAFT → SUBMITTED")
        void operatorSubmit() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.DRAFT, PoStatus.SUBMITTED, ActorType.OPERATOR);
        }

        @Test
        @DisplayName("SYSTEM: CONFIRMED → PARTIALLY_RECEIVED")
        void systemPartialReceive() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.CONFIRMED, PoStatus.PARTIALLY_RECEIVED, ActorType.SYSTEM);
        }

        @Test
        @DisplayName("SYSTEM: CONFIRMED → RECEIVED (full ASN in one shot)")
        void systemFullReceiveFromConfirmed() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.CONFIRMED, PoStatus.RECEIVED, ActorType.SYSTEM);
        }

        @Test
        @DisplayName("SYSTEM: PARTIALLY_RECEIVED → RECEIVED")
        void systemFullReceiveFromPartial() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.PARTIALLY_RECEIVED, PoStatus.RECEIVED, ActorType.SYSTEM);
        }

        @Test
        @DisplayName("SYSTEM: RECEIVED → SETTLED")
        void systemSettle() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.RECEIVED, PoStatus.SETTLED, ActorType.SYSTEM);
        }

        @Test
        @DisplayName("SYSTEM: SETTLED → CLOSED")
        void systemClose() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.SETTLED, PoStatus.CLOSED, ActorType.SYSTEM);
        }

        @Test
        @DisplayName("isTransitionAllowed mirrors ensureTransitionAllowed (true case)")
        void isTransitionAllowedReturnsTrueForAllowed() {
            assertThat(PoStatusMachine.isTransitionAllowed(
                    PoStatus.DRAFT, PoStatus.SUBMITTED, ActorType.BUYER)).isTrue();
        }
    }

    @Nested
    @DisplayName("Forbidden transitions")
    class ForbiddenTransitions {

        @Test
        @DisplayName("BUYER cannot acknowledge — that is supplier's role")
        void buyerCannotAcknowledge() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.SUBMITTED, PoStatus.ACKNOWLEDGED, ActorType.BUYER))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("BUYER cannot confirm — that is operator's role")
        void buyerCannotConfirm() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.ACKNOWLEDGED, PoStatus.CONFIRMED, ActorType.BUYER))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("SUPPLIER cannot cancel")
        void supplierCannotCancel() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.SUBMITTED, PoStatus.CANCELED, ActorType.SUPPLIER))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("SUPPLIER cannot confirm")
        void supplierCannotConfirm() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.ACKNOWLEDGED, PoStatus.CONFIRMED, ActorType.SUPPLIER))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("OPERATOR cannot drive system-only transitions")
        void operatorCannotReceive() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.CONFIRMED, PoStatus.RECEIVED, ActorType.OPERATOR))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("OPERATOR cannot settle")
        void operatorCannotSettle() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.RECEIVED, PoStatus.SETTLED, ActorType.OPERATOR))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("SYSTEM cannot cancel")
        void systemCannotCancel() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.SUBMITTED, PoStatus.CANCELED, ActorType.SYSTEM))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("Cannot cancel after CONFIRMED — no buyer / operator path")
        void cannotCancelOnceConfirmed() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.CONFIRMED, PoStatus.CANCELED, ActorType.BUYER))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.CONFIRMED, PoStatus.CANCELED, ActorType.OPERATOR))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("Cannot skip ACKNOWLEDGED step")
        void cannotSkipAcknowledged() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.SUBMITTED, PoStatus.CONFIRMED, ActorType.OPERATOR))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("Cannot skip from DRAFT directly to ACKNOWLEDGED")
        void cannotSkipFromDraft() {
            assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                    PoStatus.DRAFT, PoStatus.ACKNOWLEDGED, ActorType.SUPPLIER))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }

        @Test
        @DisplayName("isTransitionAllowed returns false for forbidden transitions")
        void isTransitionAllowedReturnsFalseForForbidden() {
            assertThat(PoStatusMachine.isTransitionAllowed(
                    PoStatus.DRAFT, PoStatus.RECEIVED, ActorType.SYSTEM)).isFalse();
        }
    }

    @Nested
    @DisplayName("Terminal states reject every outbound transition")
    class TerminalStateGuards {

        @ParameterizedTest
        @EnumSource(value = ActorType.class)
        @DisplayName("CANCELED is terminal — no actor can move out")
        void canceledIsTerminal(ActorType actor) {
            for (PoStatus to : PoStatus.values()) {
                if (to == PoStatus.CANCELED) continue;
                assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                        PoStatus.CANCELED, to, actor))
                        .as("CANCELED → %s by %s must throw", to, actor)
                        .isInstanceOf(PoStatusTransitionInvalidException.class);
            }
        }

        @ParameterizedTest
        @EnumSource(value = ActorType.class)
        @DisplayName("CLOSED is terminal — no actor can move out")
        void closedIsTerminal(ActorType actor) {
            for (PoStatus to : PoStatus.values()) {
                if (to == PoStatus.CLOSED) continue;
                assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(
                        PoStatus.CLOSED, to, actor))
                        .as("CLOSED → %s by %s must throw", to, actor)
                        .isInstanceOf(PoStatusTransitionInvalidException.class);
            }
        }
    }

    @Nested
    @DisplayName("Self-transition guards")
    class SelfTransitionGuards {

        @ParameterizedTest
        @EnumSource(value = PoStatus.class)
        @DisplayName("Every actor rejects status → same status")
        void selfTransitionAlwaysForbidden(PoStatus state) {
            for (ActorType actor : ActorType.values()) {
                assertThatThrownBy(() -> PoStatusMachine.ensureTransitionAllowed(state, state, actor))
                        .as("%s → %s by %s must throw", state, state, actor)
                        .isInstanceOf(PoStatusTransitionInvalidException.class);
            }
        }
    }

    @Nested
    @DisplayName("Linear happy path traversal")
    class LinearHappyPath {

        @Test
        @DisplayName("DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → PARTIALLY_RECEIVED → RECEIVED → SETTLED → CLOSED")
        void fullLifecycle() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.DRAFT, PoStatus.SUBMITTED, ActorType.BUYER);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.SUBMITTED, PoStatus.ACKNOWLEDGED, ActorType.SUPPLIER);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.ACKNOWLEDGED, PoStatus.CONFIRMED, ActorType.OPERATOR);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.CONFIRMED, PoStatus.PARTIALLY_RECEIVED, ActorType.SYSTEM);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.PARTIALLY_RECEIVED, PoStatus.RECEIVED, ActorType.SYSTEM);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.RECEIVED, PoStatus.SETTLED, ActorType.SYSTEM);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.SETTLED, PoStatus.CLOSED, ActorType.SYSTEM);
        }

        @Test
        @DisplayName("Express path — full ASN in one shot skips PARTIALLY_RECEIVED")
        void expressPath() {
            PoStatusMachine.ensureTransitionAllowed(PoStatus.DRAFT, PoStatus.SUBMITTED, ActorType.BUYER);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.SUBMITTED, PoStatus.ACKNOWLEDGED, ActorType.SUPPLIER);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.ACKNOWLEDGED, PoStatus.CONFIRMED, ActorType.OPERATOR);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.CONFIRMED, PoStatus.RECEIVED, ActorType.SYSTEM);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.RECEIVED, PoStatus.SETTLED, ActorType.SYSTEM);
            PoStatusMachine.ensureTransitionAllowed(PoStatus.SETTLED, PoStatus.CLOSED, ActorType.SYSTEM);
        }

        @ParameterizedTest
        @CsvSource({
                "DRAFT, BUYER",
                "SUBMITTED, BUYER",
                "ACKNOWLEDGED, OPERATOR"
        })
        @DisplayName("Cancel branches reachable from any pre-CONFIRMED status")
        void cancelBranches(PoStatus from, ActorType actor) {
            PoStatusMachine.ensureTransitionAllowed(from, PoStatus.CANCELED, actor);
        }
    }
}
