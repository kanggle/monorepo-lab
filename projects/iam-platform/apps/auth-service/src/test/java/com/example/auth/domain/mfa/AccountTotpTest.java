package com.example.auth.domain.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountTotp 도메인 (TASK-MONO-771 S2b)")
class AccountTotpTest {

    private static final Instant T0 = Instant.parse("2026-10-08T00:00:00Z");

    private static AccountTotp pending() {
        return AccountTotp.pending("acc-1", "fan-platform", new byte[]{1, 2, 3}, "v1", T0);
    }

    @Test
    @DisplayName("대기 행: 미확정 · 10분 뒤 만료 · 복구 코드 열 없음")
    void pendingLifecycle() {
        AccountTotp totp = pending();
        assertThat(totp.isConfirmed()).isFalse();
        assertThat(totp.hasNoRecoveryCodeColumn()).isTrue();
        assertThat(totp.isPendingExpired(T0.plusSeconds(599))).isFalse();
        assertThat(totp.isPendingExpired(T0.plusSeconds(601))).isTrue();
    }

    @Test
    @DisplayName("확정 → 받아들인 step 기록 · 복구 코드 저장 · 다시 확정은 거부")
    void confirm() {
        AccountTotp totp = pending();
        totp.confirm(100L, List.of("h1", "h2"), T0);

        assertThat(totp.isConfirmed()).isTrue();
        assertThat(totp.getLastUsedStep()).isEqualTo(100L);
        assertThat(totp.remainingRecoveryCodes()).isEqualTo(2);
        assertThat(totp.isPendingExpired(T0.plusSeconds(3600))).isFalse();
        assertThatThrownBy(() -> totp.confirm(101L, List.of(), T0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("복구 코드 소비는 그 원소만 지운다 · 재발급은 전체 교체")
    void recoveryCodes() {
        AccountTotp totp = pending();
        totp.confirm(100L, List.of("h1", "h2", "h3"), T0);

        totp.consumeRecoveryCode(1, T0);
        assertThat(totp.getRecoveryCodeHashes()).containsExactly("h1", "h3");

        totp.replaceRecoveryCodes(List.of("n1"), T0);
        assertThat(totp.getRecoveryCodeHashes()).containsExactly("n1");
    }

    @Test
    @DisplayName("대기 행은 2단계 성공을 기록할 수 없다")
    void pendingCannotRecordSuccess() {
        assertThatThrownBy(() -> pending().recordAuthenticatorSuccess(5L, T0))
                .isInstanceOf(IllegalStateException.class);
    }
}
