package com.example.auth.application;

import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.domain.repository.SocialIdentityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * TASK-BE-600 — the single account-status rule shared by the social callback and the
 * password form.
 */
@DisplayName("AccountStatusRule — 로그인 상태 규칙 (소셜 · 폼 공유)")
class AccountStatusRuleTest {

    @Test
    @DisplayName("ACTIVE → 통과")
    void active_proceeds() {
        assertThatCode(() -> AccountStatusRule.enforce("ACTIVE")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LOCKED → AccountLockedException")
    void locked_rejected() {
        assertThatThrownBy(() -> AccountStatusRule.enforce("LOCKED"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("DORMANT → AccountStatusException(ACCOUNT_DORMANT)")
    void dormant_rejected() {
        assertThatThrownBy(() -> AccountStatusRule.enforce("DORMANT"))
                .isInstanceOfSatisfying(AccountStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo("ACCOUNT_DORMANT"));
    }

    @Test
    @DisplayName("DELETED → AccountStatusException(ACCOUNT_DELETED)")
    void deleted_rejected() {
        assertThatThrownBy(() -> AccountStatusRule.enforce("DELETED"))
                .isInstanceOfSatisfying(AccountStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo("ACCOUNT_DELETED"));
    }

    @Test
    @DisplayName("계약 밖 값 → 거부(허용으로 읽지 않는다)")
    void unknown_rejected() {
        assertThatThrownBy(() -> AccountStatusRule.enforce("SUSPENDED"))
                .isInstanceOfSatisfying(AccountStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo("ACCOUNT_STATUS_UNKNOWN"));
    }

    @Test
    @DisplayName("TASK-BE-602: eventFailureReason — 계약 enum 값만 돌려주고, 계약 밖 · 무관한 예외는 null")
    void eventFailureReason_onlyContractValues() {
        org.assertj.core.api.Assertions.assertThat(
                AccountStatusRule.eventFailureReason(new AccountLockedException())).isEqualTo("ACCOUNT_LOCKED");
        org.assertj.core.api.Assertions.assertThat(AccountStatusRule.eventFailureReason(
                new AccountStatusException("DORMANT", "ACCOUNT_DORMANT"))).isEqualTo("ACCOUNT_DORMANT");
        org.assertj.core.api.Assertions.assertThat(AccountStatusRule.eventFailureReason(
                new AccountStatusException("DELETED", "ACCOUNT_DELETED"))).isEqualTo("ACCOUNT_DELETED");
        org.assertj.core.api.Assertions.assertThat(AccountStatusRule.eventFailureReason(
                new AccountStatusException("X", AccountStatusRule.CODE_UNKNOWN))).isNull();
        org.assertj.core.api.Assertions.assertThat(
                AccountStatusRule.eventFailureReason(new IllegalStateException("db"))).isNull();
    }

    @Test
    @DisplayName("소셜 경로(SocialLoginSteps)는 같은 규칙을 쓴다 — LOCKED 를 똑같이 거부")
    void socialPath_usesTheSameRule() {
        SocialLoginSteps steps = new SocialLoginSteps(mock(SocialIdentityRepository.class));

        assertThatThrownBy(() -> steps.checkAccountStatus("LOCKED"))
                .isInstanceOf(AccountLockedException.class);
        assertThatCode(() -> steps.checkAccountStatus("ACTIVE")).doesNotThrowAnyException();
    }
}
