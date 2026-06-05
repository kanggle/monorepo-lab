package com.example.account.domain.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountStatusMachine 상태 전이 규칙 테스트")
class AccountStatusMachineTest {

    private AccountStatusMachine machine;

    @BeforeEach
    void setUp() {
        machine = new AccountStatusMachine();
    }

    @Nested
    @DisplayName("허용된 전이")
    class AllowedTransitions {

        static Stream<Arguments> allowedTransitions() {
            return Stream.of(
                    // ACTIVE -> LOCKED
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK),
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.LOCKED, StatusChangeReason.AUTO_DETECT),
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.LOCKED, StatusChangeReason.PASSWORD_FAILURE_THRESHOLD),
                    // ACTIVE -> DORMANT
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.DORMANT, StatusChangeReason.DORMANT_365D),
                    // ACTIVE -> DELETED
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.DELETED, StatusChangeReason.USER_REQUEST),
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.DELETED, StatusChangeReason.ADMIN_DELETE),
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.DELETED, StatusChangeReason.REGULATED_DELETION),
                    // LOCKED -> ACTIVE
                    Arguments.of(AccountStatus.LOCKED, AccountStatus.ACTIVE, StatusChangeReason.ADMIN_UNLOCK),
                    Arguments.of(AccountStatus.LOCKED, AccountStatus.ACTIVE, StatusChangeReason.USER_RECOVERY),
                    // LOCKED -> DELETED
                    Arguments.of(AccountStatus.LOCKED, AccountStatus.DELETED, StatusChangeReason.ADMIN_DELETE),
                    Arguments.of(AccountStatus.LOCKED, AccountStatus.DELETED, StatusChangeReason.REGULATED_DELETION),
                    // DORMANT -> ACTIVE
                    Arguments.of(AccountStatus.DORMANT, AccountStatus.ACTIVE, StatusChangeReason.USER_LOGIN),
                    // DORMANT -> DELETED
                    Arguments.of(AccountStatus.DORMANT, AccountStatus.DELETED, StatusChangeReason.ADMIN_DELETE),
                    Arguments.of(AccountStatus.DORMANT, AccountStatus.DELETED, StatusChangeReason.REGULATED_DELETION),
                    // DELETED -> ACTIVE (grace period)
                    Arguments.of(AccountStatus.DELETED, AccountStatus.ACTIVE, StatusChangeReason.WITHIN_GRACE_PERIOD)
            );
        }

        @ParameterizedTest(name = "{0} -> {1} with reason {2}")
        @MethodSource("allowedTransitions")
        @DisplayName("허용된 전이가 정상 수행된���")
        void transition_allowed_succeeds(AccountStatus from, AccountStatus to, StatusChangeReason reason) {
            StatusTransition result = machine.transition(from, to, reason);

            assertThat(result.from()).isEqualTo(from);
            assertThat(result.to()).isEqualTo(to);
            assertThat(result.reason()).isEqualTo(reason);
        }
    }

    @Nested
    @DisplayName("금지된 전이")
    class ForbiddenTransitions {

        static Stream<Arguments> forbiddenTransitions() {
            return Stream.of(
                    // DELETED -> LOCKED (sideways)
                    Arguments.of(AccountStatus.DELETED, AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK),
                    // DELETED -> DORMANT (sideways)
                    Arguments.of(AccountStatus.DELETED, AccountStatus.DORMANT, StatusChangeReason.DORMANT_365D),
                    // DORMANT -> LOCKED (sideways)
                    Arguments.of(AccountStatus.DORMANT, AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK),
                    // ACTIVE -> LOCKED with wrong reason
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.LOCKED, StatusChangeReason.USER_REQUEST),
                    // LOCKED -> DORMANT
                    Arguments.of(AccountStatus.LOCKED, AccountStatus.DORMANT, StatusChangeReason.DORMANT_365D),
                    // ACTIVE -> ACTIVE with wrong reason
                    Arguments.of(AccountStatus.ACTIVE, AccountStatus.DORMANT, StatusChangeReason.ADMIN_LOCK)
            );
        }

        @ParameterizedTest(name = "{0} -> {1} with reason {2}")
        @MethodSource("forbiddenTransitions")
        @DisplayName("금지된 전이가 예외를 발생시킨다")
        void transition_forbidden_throwsStateTransitionException(AccountStatus from, AccountStatus to, StatusChangeReason reason) {
            assertThatThrownBy(() -> machine.transition(from, to, reason))
                    .isInstanceOf(StateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("동일 상태 전이 (멱등)")
    class SameStateTransitions {

        @Test
        @DisplayName("같은 상태로의 전이는 멱등으로 허용된다")
        void transition_sameState_returnsIdempotent() {
            StatusTransition result = machine.transition(
                    AccountStatus.LOCKED, AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK);

            assertThat(result.from()).isEqualTo(AccountStatus.LOCKED);
            assertThat(result.to()).isEqualTo(AccountStatus.LOCKED);
        }
    }

    @Nested
    @DisplayName("isAllowed 검증")
    class IsAllowedTests {

        @Test
        @DisplayName("허용된 전이에 대해 true를 반환한다")
        void isAllowed_allowedTransition_returnsTrue() {
            assertThat(machine.isAllowed(AccountStatus.ACTIVE, AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK))
                    .isTrue();
        }

        @Test
        @DisplayName("금지된 전이에 대해 false를 반환한다")
        void isAllowed_forbiddenTransition_returnsFalse() {
            assertThat(machine.isAllowed(AccountStatus.DELETED, AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK))
                    .isFalse();
        }
    }
}
