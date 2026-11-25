package com.example.auth.infrastructure.metrics;

import com.example.auth.application.port.AuthMetricsPort.IssuedTokenType;
import com.example.auth.application.port.AuthMetricsPort.LoginOutcome;
import com.example.auth.application.port.AuthMetricsPort.RefreshOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
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

    @Test
    @DisplayName("TASK-BE-668: auth_token_issued_total{type} has exactly access/refresh, both at 0, each counted on its own")
    void tokenIssuedCounterHasTheTwoSpecTypes() {
        AuthMetricsProbe probe = new AuthMetricsProbe();
        assertThat(probe.registry().find(AuthMetricsProbe.TOKEN_ISSUED).counters())
                .extracting(c -> c.getId().getTag("type"))
                .containsExactlyInAnyOrderElementsOf(AuthMetricsProbe.ISSUED_TYPES);
        assertThat(probe.issued("access")).isZero();
        assertThat(probe.issued("refresh")).isZero();

        probe.port().tokenIssued(IssuedTokenType.ACCESS);
        probe.port().tokenIssued(IssuedTokenType.ACCESS);
        probe.port().tokenIssued(IssuedTokenType.REFRESH);

        assertThat(probe.issued("access")).isEqualTo(2.0);
        assertThat(probe.issued("refresh")).isEqualTo(1.0);
        probe.assertNoRefresh();
    }

    @Test
    @DisplayName("TASK-BE-668: auth_token_refresh_total{result} has exactly the spec's four results; each outcome increments only its own")
    void refreshCounterHasTheFourSpecResults() {
        AuthMetricsProbe fresh = new AuthMetricsProbe();
        assertThat(fresh.registry().find(AuthMetricsProbe.TOKEN_REFRESH).counters())
                .extracting(c -> c.getId().getTag("result"))
                .containsExactlyInAnyOrderElementsOf(AuthMetricsProbe.REFRESH_RESULTS);
        assertThat(Arrays.stream(RefreshOutcome.values()).map(RefreshOutcome::tagValue))
                .containsExactlyInAnyOrderElementsOf(AuthMetricsProbe.REFRESH_RESULTS);
        fresh.assertNoRefresh();

        for (RefreshOutcome outcome : RefreshOutcome.values()) {
            AuthMetricsProbe probe = new AuthMetricsProbe();
            probe.port().refreshAttempt(outcome);
            probe.assertOnlyRefresh(outcome.tagValue());
            assertThat(probe.tokenReuse()).isZero();  // the reuse KPI is a separate call
        }
    }

    @Test
    @DisplayName("TASK-BE-668 AC-1: a real PrometheusMeterRegistry scrape carries the spec names and labels verbatim")
    void prometheusScrapeCarriesTheSpecNames() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerAuthMetrics metrics = new MicrometerAuthMetrics(prometheus);
        metrics.tokenIssued(IssuedTokenType.ACCESS);
        metrics.refreshAttempt(RefreshOutcome.REUSE_DETECTED);

        String scrape = prometheus.scrape();

        assertThat(scrape).contains("auth_token_issued_total{type=\"access\"} 1.0");
        assertThat(scrape).contains("auth_token_issued_total{type=\"refresh\"} 0.0");
        for (String result : AuthMetricsProbe.REFRESH_RESULTS) {
            assertThat(scrape).as(scrape).contains("auth_token_refresh_total{result=\"" + result + "\"} "
                    + ("reuse_detected".equals(result) ? "1.0" : "0.0"));
        }
        // the 664 names still export as the spec writes them
        assertThat(scrape).contains("auth_login_total{result=\"failure\"} 0.0");
        assertThat(scrape).contains("auth_token_reuse_detected_total 0.0");
    }
}
