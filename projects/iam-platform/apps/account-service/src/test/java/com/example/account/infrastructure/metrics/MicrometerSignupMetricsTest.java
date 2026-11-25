package com.example.account.infrastructure.metrics;

import com.example.account.application.port.SignupMetricsPort.SignupOutcome;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-668 — {@code account_signup_total{result}} exactly as account-service observability.md :13
 * writes it: the three result values, all present (as 0) from construction, each counted on its own,
 * and exported to Prometheus under the spec name.
 */
class MicrometerSignupMetricsTest {

    @Test
    @DisplayName("the counter has exactly the spec's three result values, all registered at 0")
    void hasTheThreeSpecResults() {
        SignupMetricsProbe probe = new SignupMetricsProbe();

        assertThat(probe.registry().find(SignupMetricsProbe.SIGNUP).counters())
                .extracting(c -> c.getId().getTag("result"))
                .containsExactlyInAnyOrderElementsOf(SignupMetricsProbe.RESULTS);
        assertThat(Arrays.stream(SignupOutcome.values()).map(SignupOutcome::tagValue))
                .containsExactlyInAnyOrderElementsOf(SignupMetricsProbe.RESULTS);
        probe.assertNone();
    }

    @Test
    @DisplayName("each SignupOutcome increments only its own series")
    void eachOutcomeIncrementsItsOwnSeries() {
        for (SignupOutcome outcome : SignupOutcome.values()) {
            SignupMetricsProbe probe = new SignupMetricsProbe();
            probe.port().signupAttempt(outcome);
            probe.assertOnly(outcome.tagValue());
        }
    }

    @Test
    @DisplayName("AC-1: a real PrometheusMeterRegistry scrape carries account_signup_total{result=…} verbatim")
    void prometheusScrapeCarriesTheSpecName() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerSignupMetrics metrics = new MicrometerSignupMetrics(prometheus);
        metrics.signupAttempt(SignupOutcome.DUPLICATE);

        String scrape = prometheus.scrape();

        assertThat(scrape).as(scrape).contains("account_signup_total{result=\"duplicate\"} 1.0");
        assertThat(scrape).contains("account_signup_total{result=\"success\"} 0.0");
        assertThat(scrape).contains("account_signup_total{result=\"validation_error\"} 0.0");
    }
}
