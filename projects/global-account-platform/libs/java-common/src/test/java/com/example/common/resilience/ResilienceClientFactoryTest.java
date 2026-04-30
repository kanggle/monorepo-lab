package com.example.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceClientFactoryTest {

    // -------- buildRestClient --------

    @Test
    @DisplayName("buildRestClient returns a non-null RestClient with the given baseUrl")
    void buildRestClient_returnsNonNull() {
        RestClient client = ResilienceClientFactory.buildRestClient(
                "http://localhost:1234", Duration.ofSeconds(3), Duration.ofSeconds(5));
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("buildRestClient millisecond overload accepts int timeouts")
    void buildRestClient_millisOverload() {
        RestClient client = ResilienceClientFactory.buildRestClient(
                "http://localhost:1234", 3000, 5000);
        assertThat(client).isNotNull();
    }

    // -------- standardCircuitBreakerConfig --------

    @Test
    @DisplayName("standardCircuitBreakerConfig defaults match documented standard")
    void standardCircuitBreakerConfig_matchesDocumentedDefaults() {
        CircuitBreakerConfig cfg = ResilienceClientFactory.standardCircuitBreakerConfig().build();

        assertThat(cfg.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(cfg.getSlidingWindowType())
                .isEqualTo(CircuitBreakerConfig.SlidingWindowType.TIME_BASED);
        assertThat(cfg.getSlidingWindowSize()).isEqualTo(10);
        assertThat(cfg.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(cfg.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        // Resilience4j 2.x exposes wait-in-open as an IntervalFunction (not a direct Duration).
        // For a constant 10s wait configured via waitDurationInOpenState(Duration.ofSeconds(10)),
        // the function returns 10_000ms regardless of attempt index.
        assertThat(cfg.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(10).toMillis());
    }

    // -------- buildCircuitBreaker --------

    @Test
    @DisplayName("buildCircuitBreaker(name) creates breaker named with the standard config")
    void buildCircuitBreaker_default() {
        CircuitBreaker cb = ResilienceClientFactory.buildCircuitBreaker("svc-A");

        assertThat(cb.getName()).isEqualTo("svc-A");
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
    }

    @Test
    @DisplayName("buildCircuitBreaker(name, customizer) overrides only the customized field")
    void buildCircuitBreaker_customizerOverridesField() {
        CircuitBreaker cb = ResilienceClientFactory.buildCircuitBreaker(
                "svc-B", b -> b.failureRateThreshold(80));

        assertThat(cb.getName()).isEqualTo("svc-B");
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(80.0f);
        // Other defaults preserved
        assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
    }

    // -------- standardRetryConfig --------

    @Test
    @DisplayName("standardRetryConfig: 3 attempts and ignores HttpClientErrorException")
    void standardRetryConfig_defaults() {
        RetryConfig cfg = ResilienceClientFactory.standardRetryConfig().build();

        assertThat(cfg.getMaxAttempts()).isEqualTo(3);
        // 4xx is non-retryable: HttpClientErrorException must NOT be marked as retryable
        assertThat(cfg.getExceptionPredicate().test(new HttpClientErrorException(
                org.springframework.http.HttpStatus.BAD_REQUEST))).isFalse();
        // Generic exceptions ARE retryable
        assertThat(cfg.getExceptionPredicate().test(new RuntimeException("boom"))).isTrue();
    }

    // -------- buildRetry --------

    @Test
    @DisplayName("buildRetry(name) creates retry named with the standard config")
    void buildRetry_default() {
        Retry retry = ResilienceClientFactory.buildRetry("svc-A");

        assertThat(retry.getName()).isEqualTo("svc-A");
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("buildRetry(name, customizer) overrides only the customized field")
    void buildRetry_customizerOverridesField() {
        Retry retry = ResilienceClientFactory.buildRetry(
                "svc-B", b -> b.maxAttempts(5));

        assertThat(retry.getName()).isEqualTo("svc-B");
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(5);
        // Default: HttpClientErrorException still ignored
        assertThat(retry.getRetryConfig().getExceptionPredicate()
                .test(new HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND)))
                .isFalse();
    }
}
