package com.example.auth.infrastructure.client;

import io.micrometer.observation.ObservationRegistry;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerMetricsAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakersHealthIndicatorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TASK-BE-665 — {@code AuthAccountServiceDown} queries
 * {@code resilience4j_circuitbreaker_state{name="accountService",state=~"open|forced_open"}}.
 *
 * <p>The context is Spring Boot's metrics + Prometheus auto-configuration together with
 * resilience4j-spring-boot3's own circuit-breaker auto-configuration — the same pieces the running
 * auth-service gets — and every assertion reads the real Prometheus scrape text, so the label names
 * the alert uses are the ones checked here.
 */
class AccountServiceCircuitBreakerMetricsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class,
                    PrometheusMetricsExportAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    CircuitBreakerMetricsAutoConfiguration.class,
                    CircuitBreakersHealthIndicatorAutoConfiguration.class));

    private static AccountServiceClient productionClient(CircuitBreakerRegistry registry) {
        return new AccountServiceClient(new MockEnvironment(), 3000, 5000,
                mock(IamClientCredentialsTokenProvider.class), registry, ObservationRegistry.NOOP);
    }

    private static String stateLine(String scrape, String state) {
        return Arrays.stream(scrape.split("\n"))
                .filter(l -> l.startsWith("resilience4j_circuitbreaker_state{"))
                .filter(l -> l.contains("name=\"accountService\""))
                .filter(l -> l.contains("state=\"" + state + "\""))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("① the production constructor's breaker is exported as resilience4j_circuitbreaker_state{name=\"accountService\"}")
    void registryBreaker_isExported() {
        runner.run(context -> {
            productionClient(context.getBean(CircuitBreakerRegistry.class));
            String scrape = context.getBean(PrometheusMeterRegistry.class).scrape();

            assertThat(stateLine(scrape, "closed")).as(scrape).isNotNull().endsWith(" 1.0");
            assertThat(stateLine(scrape, "open")).isNotNull().endsWith(" 0.0");
        });
    }

    @Test
    @DisplayName("② control — a standalone CircuitBreaker.of(\"accountService\") (the pre-665 shape) is NOT exported")
    void standaloneBreaker_isNotExported_control() {
        runner.run(context -> {
            CircuitBreaker standalone = ResilienceClientFactory.buildCircuitBreaker("accountService");
            standalone.transitionToOpenState();
            String scrape = context.getBean(PrometheusMeterRegistry.class).scrape();

            assertThat(scrape).doesNotContain("name=\"accountService\"");
        });
    }

    @Test
    @DisplayName("③ an open breaker reads 1 on the state=\"open\" line — what the alert fires on")
    void openBreaker_readsOne() {
        runner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            productionClient(registry);
            registry.circuitBreaker(AccountServiceClient.CIRCUIT_BREAKER_NAME).transitionToOpenState();
            String scrape = context.getBean(PrometheusMeterRegistry.class).scrape();

            assertThat(stateLine(scrape, "open")).as(scrape).isNotNull().endsWith(" 1.0");
            assertThat(stateLine(scrape, "closed")).isNotNull().endsWith(" 0.0");
            // the alert's other labels
            assertThat(stateLine(scrape, "forced_open")).isNotNull();
        });
    }

    @Test
    @DisplayName("⑤ moving the breaker into the registry does not let an open breaker turn auth-service health DOWN")
    void openBreaker_doesNotTurnHealthDown() {
        // (a) Default: resilience4j's circuit-breaker health indicator is off
        // (management.health.circuitbreakers.enabled defaults to false in its metadata, and
        // auth-service's application.yml does not set it) — so no breaker reaches /actuator/health.
        runner.withConfiguration(AutoConfigurations.of(
                        HealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean("circuitBreakersHealthIndicator"));

        // (b) Worst case, forced on: still not DOWN, because the indicator includes only breakers
        // whose instance properties set registerHealthIndicator=true and accountService has none.
        // An account-service outage must not get auth-service restarted — that fixes nothing.
        runner.withConfiguration(AutoConfigurations.of(
                        HealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class))
                .withPropertyValues("management.health.circuitbreakers.enabled=true")
                .run(context -> {
                    CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
                    productionClient(registry);
                    registry.circuitBreaker(AccountServiceClient.CIRCUIT_BREAKER_NAME).transitionToOpenState();

                    assertThat(context).hasBean("circuitBreakersHealthIndicator");
                    HealthIndicator indicator =
                            context.getBean("circuitBreakersHealthIndicator", HealthIndicator.class);
                    assertThat(indicator.health().getStatus()).isNotEqualTo(Status.DOWN);
                    assertThat(indicator.health().getDetails())
                            .doesNotContainKey(AccountServiceClient.CIRCUIT_BREAKER_NAME);
                });
    }

    @Test
    @DisplayName("④ the registry breaker keeps the standard ResilienceClientFactory config (no behaviour change)")
    void registryBreaker_keepsStandardConfig() {
        runner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            productionClient(registry);

            assertThat(registry.find(AccountServiceClient.CIRCUIT_BREAKER_NAME)).isPresent();
            CircuitBreakerConfig actual = registry.circuitBreaker(AccountServiceClient.CIRCUIT_BREAKER_NAME)
                    .getCircuitBreakerConfig();
            CircuitBreakerConfig standard = ResilienceClientFactory.standardCircuitBreakerConfig().build();
            assertThat(actual.getFailureRateThreshold()).isEqualTo(standard.getFailureRateThreshold());
            assertThat(actual.getSlidingWindowSize()).isEqualTo(standard.getSlidingWindowSize());
            assertThat(actual.getSlidingWindowType()).isEqualTo(standard.getSlidingWindowType());
            assertThat(actual.getMinimumNumberOfCalls()).isEqualTo(standard.getMinimumNumberOfCalls());
            assertThat(actual.getPermittedNumberOfCallsInHalfOpenState())
                    .isEqualTo(standard.getPermittedNumberOfCallsInHalfOpenState());
            assertThat(actual.getWaitIntervalFunctionInOpenState().apply(1))
                    .isEqualTo(standard.getWaitIntervalFunctionInOpenState().apply(1));
        });
    }
}
