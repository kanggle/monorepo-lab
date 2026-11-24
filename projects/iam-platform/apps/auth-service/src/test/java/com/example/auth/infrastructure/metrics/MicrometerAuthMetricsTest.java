package com.example.auth.infrastructure.metrics;

import com.example.auth.application.port.AuthMetricsPort.LoginOutcome;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-664 — the metric names and tags exactly as auth-service observability.md :13 · :17 writes
 * them, and every series present (as 0) from construction so the alert rules never read no-data.
 */
class MicrometerAuthMetricsTest {

    @Test
    @DisplayName("auth_login_total{result} has exactly the spec's four result values, all registered at 0")
    void loginCounterHasTheFourSpecResults() {
        AuthMetricsProbe probe = new AuthMetricsProbe();

        assertThat(probe.registry().find(AuthMetricsProbe.LOGIN).counters())
                .extracting(c -> c.getId().getTag("result"))
                .containsExactlyInAnyOrderElementsOf(AuthMetricsProbe.LOGIN_RESULTS);
        assertThat(Arrays.stream(LoginOutcome.values()).map(LoginOutcome::tagValue))
                .containsExactlyInAnyOrderElementsOf(AuthMetricsProbe.LOGIN_RESULTS);
        probe.assertNoLogin();
    }

    @Test
    @DisplayName("each LoginOutcome increments only its own series")
    void eachOutcomeIncrementsItsOwnSeries() {
        for (LoginOutcome outcome : LoginOutcome.values()) {
            AuthMetricsProbe probe = new AuthMetricsProbe();
            probe.port().loginAttempt(outcome);
            probe.assertOnlyLogin(outcome.tagValue());
            assertThat(probe.tokenReuse()).isZero();
        }
    }

    @Test
    @DisplayName("auth_token_reuse_detected_total has no tags, starts at 0 and counts one per call")
    void tokenReuseCounterHasNoTags() {
        AuthMetricsProbe probe = new AuthMetricsProbe();
        Counter counter = probe.registry().get(AuthMetricsProbe.TOKEN_REUSE).counter();
        assertThat(counter.getId().getTags()).isEmpty();
        assertThat(counter.count()).isZero();

        probe.port().tokenReuseDetected();
        probe.port().tokenReuseDetected();

        assertThat(probe.tokenReuse()).isEqualTo(2.0);
        probe.assertNoLogin();
    }
}
