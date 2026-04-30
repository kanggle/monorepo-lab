package com.example.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Central factory for resilient outbound HTTP clients.
 *
 * <p>Standardizes the three pieces every internal service-to-service client
 * was previously hand-rolling:
 * <ol>
 *   <li>JDK {@link HttpClient} + {@link RestClient} with explicit connect/read
 *       timeouts (no JVM-default infinite reads).</li>
 *   <li>{@link CircuitBreaker} with TIME_BASED 10s window, 50% failure rate,
 *       minimum 5 calls, 10s wait-in-open, 3 half-open calls.</li>
 *   <li>{@link Retry} with 3 attempts, exponential-random backoff (500ms base,
 *       x2 multiplier, ±50% jitter), and {@link HttpClientErrorException} as
 *       a non-retryable terminal exception.</li>
 * </ol>
 *
 * <p>Each factory method has a "standard defaults" overload and a customizer
 * overload so callers can override individual settings (e.g. a service that
 * needs a longer read timeout) without re-stating the rest of the config.
 *
 * <p>Pure static utility — no Spring bean, no field state, safe to call from
 * any layer.
 */
public final class ResilienceClientFactory {

    private ResilienceClientFactory() {
    }

    // -------- HTTP client --------

    /**
     * Build a {@link RestClient} backed by the JDK {@link HttpClient} with
     * explicit connect and read timeouts.
     */
    public static RestClient buildRestClient(String baseUrl,
                                             Duration connectTimeout,
                                             Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Convenience overload for callers that pass timeouts in milliseconds. */
    public static RestClient buildRestClient(String baseUrl,
                                             int connectTimeoutMs,
                                             int readTimeoutMs) {
        return buildRestClient(baseUrl,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
    }

    // -------- Circuit breaker --------

    /** Build a circuit breaker using the standard defaults. */
    public static CircuitBreaker buildCircuitBreaker(String name) {
        return CircuitBreaker.of(name, standardCircuitBreakerConfig().build());
    }

    /**
     * Build a circuit breaker, applying {@code customizer} on top of the
     * standard defaults so callers only state what is different.
     */
    public static CircuitBreaker buildCircuitBreaker(String name,
                                                     Consumer<CircuitBreakerConfig.Builder> customizer) {
        CircuitBreakerConfig.Builder builder = standardCircuitBreakerConfig();
        customizer.accept(builder);
        return CircuitBreaker.of(name, builder.build());
    }

    // -------- Retry --------

    /** Build a retry using the standard defaults. */
    public static Retry buildRetry(String name) {
        return Retry.of(name, standardRetryConfig().build());
    }

    /**
     * Build a retry, applying {@code customizer} on top of the standard
     * defaults so callers only state what is different.
     */
    public static Retry buildRetry(String name,
                                   Consumer<RetryConfig.Builder<Object>> customizer) {
        RetryConfig.Builder<Object> builder = standardRetryConfig();
        customizer.accept(builder);
        return Retry.of(name, builder.build());
    }

    // -------- Standard config builders (exposed for tests) --------

    /**
     * Standard {@link CircuitBreakerConfig.Builder}: 50% failure rate, 10s
     * TIME_BASED window, minimum 5 calls, 10s wait-in-open, 3 half-open calls.
     */
    public static CircuitBreakerConfig.Builder standardCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3);
    }

    /**
     * Standard {@link RetryConfig.Builder}: 3 attempts (1 initial + 2 retries),
     * exponential-random backoff with 500ms base. {@link HttpClientErrorException}
     * is ignored (4xx is a contract failure — never retry); everything else is
     * retried.
     */
    public static RetryConfig.Builder<Object> standardRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(500)))
                .ignoreExceptions(HttpClientErrorException.class)
                .retryExceptions(Exception.class);
    }
}
