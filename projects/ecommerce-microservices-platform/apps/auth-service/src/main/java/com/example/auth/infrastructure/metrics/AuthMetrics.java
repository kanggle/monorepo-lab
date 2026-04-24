package com.example.auth.infrastructure.metrics;

import com.example.auth.domain.service.AuthMetricsRecorder;
import com.example.observability.metrics.EventMetricNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AuthMetrics implements AuthMetricsRecorder {

    private final MeterRegistry registry;
    private final Counter signupTotal;
    private final Counter loginSuccessTotal;
    private final Counter loginFailureTotal;
    private final Counter loginFailureInvalidCredentials;
    private final Counter loginFailureRateLimited;
    private final Counter logoutTotal;
    private final Counter tokenRefreshSuccessTotal;
    private final Counter tokenRefreshFailureTotal;
    private final Counter sessionEvictionTotal;

    public AuthMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry must not be null");
        this.registry = registry;
        this.signupTotal = buildCounter("auth_signup_total", "Total successful signups");
        this.loginSuccessTotal = buildCounter("auth_login_total", "Total login attempts", "result", "success");
        this.loginFailureTotal = buildCounter("auth_login_total", "Total login attempts", "result", "failure");
        this.loginFailureInvalidCredentials = buildCounter("auth_login_failure_total", "Total failed login attempts by reason", "reason", "invalid_credentials");
        this.loginFailureRateLimited = buildCounter("auth_login_failure_total", "Total failed login attempts by reason", "reason", "rate_limited");
        this.logoutTotal = buildCounter("auth_logout_total", "Total logout requests");
        this.tokenRefreshSuccessTotal = buildCounter("auth_token_refresh_total", "Total token refresh attempts", "result", "success");
        this.tokenRefreshFailureTotal = buildCounter("auth_token_refresh_total", "Total token refresh attempts", "result", "failure");
        this.sessionEvictionTotal = buildCounter("auth_session_eviction_total", "Total sessions evicted due to concurrent session limit");
    }

    private Counter buildCounter(String name, String description, String... tags) {
        Counter.Builder builder = Counter.builder(name).description(description);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder = builder.tag(tags[i], tags[i + 1]);
        }
        return builder.register(registry);
    }

    @Override
    public void incrementSignup() {
        signupTotal.increment();
    }

    @Override
    public void incrementLoginSuccess() {
        loginSuccessTotal.increment();
    }

    @Override
    public void incrementLoginFailure(String reason) {
        loginFailureTotal.increment();
        switch (reason) {
            case "invalid_credentials" -> loginFailureInvalidCredentials.increment();
            case "rate_limited" -> loginFailureRateLimited.increment();
            default -> loginFailureInvalidCredentials.increment();
        }
    }

    @Override
    public void incrementLogout() {
        logoutTotal.increment();
    }

    @Override
    public void incrementTokenRefreshSuccess() {
        tokenRefreshSuccessTotal.increment();
    }

    @Override
    public void incrementTokenRefreshFailure() {
        tokenRefreshFailureTotal.increment();
    }

    @Override
    public void incrementSessionEviction() {
        sessionEvictionTotal.increment();
    }

    public void incrementEventPublishFailure(String eventType) {
        registry.counter(EventMetricNames.EVENT_PUBLISH_FAILURE_TOTAL,
                EventMetricNames.TAG_SERVICE, "auth-service",
                EventMetricNames.TAG_EVENT_TYPE, eventType)
                .increment();
    }
}
