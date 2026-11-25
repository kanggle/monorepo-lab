package com.example.auth.infrastructure.metrics;

import com.example.auth.application.port.AuthMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * TASK-BE-664 — Micrometer implementation of {@link AuthMetricsPort}
 * (auth-service observability.md § 핵심 메트릭 :13 · :17; TASK-BE-668 adds :15 · :16).
 *
 * <p>Every counter is registered up front, so each {@code result} / {@code type} series is scraped as
 * {@code 0} from start-up instead of appearing only after its first occurrence — the alert rules
 * ({@code AuthLoginFailureSpike}, {@code AuthRefreshTokenReuseDetected}) and the dashboard panels
 * («Tokens Issued», «Refresh Token Rotation») then read a real zero, not no-data.
 */
@Component
public class MicrometerAuthMetrics implements AuthMetricsPort {

    /** observability.md:13 — counter, tag {@code result} (success/failure/rate_limited/account_locked). */
    static final String LOGIN_METRIC_NAME = "auth_login_total";
    /** observability.md:17 — counter, no tags. */
    static final String TOKEN_REUSE_METRIC_NAME = "auth_token_reuse_detected_total";
    /** TASK-BE-668 — observability.md:15 — counter, tag {@code type} (access/refresh). */
    static final String TOKEN_ISSUED_METRIC_NAME = "auth_token_issued_total";
    /** TASK-BE-668 — observability.md:16 — counter, tag {@code result} (success/expired/reuse_detected/revoked). */
    static final String TOKEN_REFRESH_METRIC_NAME = "auth_token_refresh_total";

    private final Map<LoginOutcome, Counter> loginCounters = new EnumMap<>(LoginOutcome.class);
    private final Counter tokenReuseCounter;
    private final Map<IssuedTokenType, Counter> issuedCounters = new EnumMap<>(IssuedTokenType.class);
    private final Map<RefreshOutcome, Counter> refreshCounters = new EnumMap<>(RefreshOutcome.class);

    public MicrometerAuthMetrics(MeterRegistry meterRegistry) {
        for (LoginOutcome outcome : LoginOutcome.values()) {
            loginCounters.put(outcome, Counter.builder(LOGIN_METRIC_NAME)
                    .description("Login attempts, one per attempt, by result")
                    .tag("result", outcome.tagValue())
                    .register(meterRegistry));
        }
        this.tokenReuseCounter = Counter.builder(TOKEN_REUSE_METRIC_NAME)
                .description("Refresh-token reuse detections (security KPI)")
                .register(meterRegistry);
        for (IssuedTokenType type : IssuedTokenType.values()) {
            issuedCounters.put(type, Counter.builder(TOKEN_ISSUED_METRIC_NAME)
                    .description("Tokens minted, one per token, by type")
                    .tag("type", type.tagValue())
                    .register(meterRegistry));
        }
        for (RefreshOutcome outcome : RefreshOutcome.values()) {
            refreshCounters.put(outcome, Counter.builder(TOKEN_REFRESH_METRIC_NAME)
                    .description("Refresh attempts, one per attempt, by result")
                    .tag("result", outcome.tagValue())
                    .register(meterRegistry));
        }
    }

    @Override
    public void loginAttempt(LoginOutcome outcome) {
        loginCounters.get(outcome).increment();
    }

    @Override
    public void tokenReuseDetected() {
        tokenReuseCounter.increment();
    }

    @Override
    public void tokenIssued(IssuedTokenType type) {
        issuedCounters.get(type).increment();
    }

    @Override
    public void refreshAttempt(RefreshOutcome outcome) {
        refreshCounters.get(outcome).increment();
    }
}
