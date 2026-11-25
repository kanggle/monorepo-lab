package com.example.account.infrastructure.metrics;

import com.example.account.application.port.SignupMetricsPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-668 test support — a real {@link MicrometerSignupMetrics} on a {@link SimpleMeterRegistry},
 * read back by the spec's metric name (a literal on purpose: the test pins the name observability.md
 * writes, not whatever the production constant says). Same shape as auth-service's
 * {@code AuthMetricsProbe} (TASK-BE-664).
 */
public final class SignupMetricsProbe {

    public static final String SIGNUP = "account_signup_total";
    public static final List<String> RESULTS = List.of("success", "duplicate", "validation_error");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerSignupMetrics port = new MicrometerSignupMetrics(registry);

    public SignupMetricsPort port() {
        return port;
    }

    public SimpleMeterRegistry registry() {
        return registry;
    }

    public double signup(String result) {
        return registry.get(SIGNUP).tag("result", result).counter().count();
    }

    /** Exactly one signup attempt was counted, and it was {@code result}. */
    public void assertOnly(String result) {
        for (String r : RESULTS) {
            assertThat(signup(r))
                    .as("account_signup_total{result=%s}", r)
                    .isEqualTo(r.equals(result) ? 1.0 : 0.0);
        }
    }

    /** No signup attempt was counted at all. */
    public void assertNone() {
        for (String r : RESULTS) {
            assertThat(signup(r)).as("account_signup_total{result=%s}", r).isZero();
        }
    }
}
