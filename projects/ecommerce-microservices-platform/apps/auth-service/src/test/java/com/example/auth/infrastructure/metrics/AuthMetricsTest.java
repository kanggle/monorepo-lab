package com.example.auth.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMetricsTest {

    private MeterRegistry registry;
    private AuthMetrics authMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        authMetrics = new AuthMetrics(registry);
    }

    @Test
    @DisplayName("회원가입 성공 시 auth_signup_total이 증가한다")
    void incrementSignup_incrementsCounter() {
        authMetrics.incrementSignup();
        authMetrics.incrementSignup();

        assertThat(registry.counter("auth_signup_total").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("로그인 성공 시 auth_login_total(result=success)이 증가한다")
    void incrementLoginSuccess_incrementsCounter() {
        authMetrics.incrementLoginSuccess();

        assertThat(registry.counter("auth_login_total", "result", "success").count()).isEqualTo(1.0);
        assertThat(registry.counter("auth_login_total", "result", "failure").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("로그인 실패 시 auth_login_total(result=failure)과 auth_login_failure_total(reason)이 증가한다")
    void incrementLoginFailure_incrementsCounterWithReason() {
        authMetrics.incrementLoginFailure("invalid_credentials");
        authMetrics.incrementLoginFailure("rate_limited");
        authMetrics.incrementLoginFailure("invalid_credentials");

        assertThat(registry.counter("auth_login_total", "result", "failure").count()).isEqualTo(3.0);
        assertThat(registry.counter("auth_login_failure_total", "reason", "invalid_credentials").count()).isEqualTo(2.0);
        assertThat(registry.counter("auth_login_failure_total", "reason", "rate_limited").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("로그아웃 시 auth_logout_total이 증가한다")
    void incrementLogout_incrementsCounter() {
        authMetrics.incrementLogout();

        assertThat(registry.counter("auth_logout_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("토큰 갱신 성공 시 auth_token_refresh_total(result=success)이 증가한다")
    void incrementTokenRefreshSuccess_incrementsCounter() {
        authMetrics.incrementTokenRefreshSuccess();

        assertThat(registry.counter("auth_token_refresh_total", "result", "success").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("토큰 갱신 실패 시 auth_token_refresh_total(result=failure)이 증가한다")
    void incrementTokenRefreshFailure_incrementsCounter() {
        authMetrics.incrementTokenRefreshFailure();

        assertThat(registry.counter("auth_token_refresh_total", "result", "failure").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("세션 퇴거 시 auth_session_eviction_total이 증가한다")
    void incrementSessionEviction_incrementsCounter() {
        authMetrics.incrementSessionEviction();
        authMetrics.incrementSessionEviction();

        assertThat(registry.counter("auth_session_eviction_total").count()).isEqualTo(2.0);
    }
}
