package com.example.auth.infrastructure.metrics;

import com.example.auth.application.port.AuthMetricsPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-664 test support — a real {@link MicrometerAuthMetrics} on a {@link SimpleMeterRegistry},
 * read back by the spec's metric names (literals, on purpose: the test pins the names
 * observability.md writes, not whatever the production constant says).
 *
 * <p>Use it as the {@code @Spy} field of an {@code @InjectMocks} test so the class under test counts
 * into a registry the test can read:
 * <pre>{@code
 * private final AuthMetricsProbe metrics = new AuthMetricsProbe();
 * @Spy private AuthMetricsPort authMetrics = metrics.port();
 * }</pre>
 */
public final class AuthMetricsProbe {

    public static final String LOGIN = "auth_login_total";
    public static final String TOKEN_REUSE = "auth_token_reuse_detected_total";
    public static final List<String> LOGIN_RESULTS =
            List.of("success", "failure", "rate_limited", "account_locked");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerAuthMetrics port = new MicrometerAuthMetrics(registry);

    public AuthMetricsPort port() {
        return port;
    }

    public SimpleMeterRegistry registry() {
        return registry;
    }

    public double login(String result) {
        return registry.get(LOGIN).tag("result", result).counter().count();
    }

    public double tokenReuse() {
        return registry.get(TOKEN_REUSE).counter().count();
    }

    /** Exactly one login attempt was counted, and it was {@code result}. */
    public void assertOnlyLogin(String result) {
        for (String r : LOGIN_RESULTS) {
            assertThat(login(r))
                    .as("auth_login_total{result=%s}", r)
                    .isEqualTo(r.equals(result) ? 1.0 : 0.0);
        }
    }

    /** No login attempt was counted at all. */
    public void assertNoLogin() {
        for (String r : LOGIN_RESULTS) {
            assertThat(login(r)).as("auth_login_total{result=%s}", r).isZero();
        }
    }
}
