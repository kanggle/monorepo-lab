package com.example.auth.infrastructure.metrics;

import com.example.auth.application.port.AuthMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * TASK-BE-664 — Micrometer implementation of {@link AuthMetricsPort}
 * (auth-service observability.md § 필수 메트릭 :13 · :17).
 *
 * <p>Every counter is registered up front, so each of the four {@code result} series is scraped as
 * {@code 0} from start-up instead of appearing only after its first occurrence — the alert rules
 * ({@code AuthLoginFailureSpike}, {@code AuthRefreshTokenReuseDetected}) then read a real zero, not
 * no-data.
 */
@Component
public class MicrometerAuthMetrics implements AuthMetricsPort {

    /** observability.md:13 — counter, tag {@code result} (success/failure/rate_limited/account_locked). */
    static final String LOGIN_METRIC_NAME = "auth_login_total";
    /** observability.md:17 — counter, no tags. */
    static final String TOKEN_REUSE_METRIC_NAME = "auth_token_reuse_detected_total";

    private final Map<LoginOutcome, Counter> loginCounters = new EnumMap<>(LoginOutcome.class);
    private final Counter tokenReuseCounter;

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
    }

    @Override
    public void loginAttempt(LoginOutcome outcome) {
        loginCounters.get(outcome).increment();
    }

    @Override
    public void tokenReuseDetected() {
        tokenReuseCounter.increment();
    }
}
