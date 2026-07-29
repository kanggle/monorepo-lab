package com.kanggle.platformconsole.bff.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-leg resilience policy for the outbound composition legs (TASK-PC-BE-015).
 *
 * <p>Bound from {@code consolebff.resilience.*}. These values feed
 * {@code com.example.common.resilience.ResilienceClientFactory}'s <b>customizer
 * overloads</b> — the shared library's defaults (failure classification,
 * {@code ignoreExceptions(HttpClientErrorException)}, jittered exponential
 * backoff) are inherited unchanged; only the settings a BFF genuinely needs to
 * differ on are overridden here.
 *
 * <h2>Why the defaults differ from the library's</h2>
 * <ul>
 *   <li><b>{@code COUNT_BASED} window</b> (the library default is
 *       {@code TIME_BASED} over 10s). A BFF leg fires roughly <i>once per operator
 *       dashboard load</i>. A time-based window would require 5 loads inside 10
 *       seconds before it could ever satisfy {@code minimumNumberOfCalls}, so on
 *       a low-traffic operator console the breaker would essentially never trip —
 *       a circuit breaker that cannot open is documentation, not resilience.
 *       Counting calls instead of seconds makes "5 consecutive failed loads"
 *       trip it regardless of how far apart the loads are.</li>
 *   <li><b>2 retry attempts / 150ms base</b> (the library default is 3 attempts /
 *       500ms base). The composition has a hard 5s deadline
 *       ({@code CompositionEngine.COMPOSITION_TIMEOUT}) and each leg a 2s
 *       timeout ({@code RestClientConfig.PER_LEG_TIMEOUT}). The library defaults
 *       would allow {@code 2s + ~0.75s + 2s + ~1.5s + 2s ≈ 8.2s}, overrunning the
 *       deadline and converting every per-leg degrade into a whole-composition
 *       {@code TIMEOUT}. At 2 attempts and a 150ms base (±50% jitter ⇒ ≤ 225ms,
 *       and ≤ 450ms even if the multiplier is applied) the worst case is
 *       {@code 2s + 0.45s + 2s ≈ 4.45s < 5s}. Asserted, not merely commented —
 *       see {@code Resilience4jLegResilienceAdapterTest}.</li>
 * </ul>
 *
 * <h2>Settings intentionally NOT exposed</h2>
 * <b>{@code waitDurationInOpenState}</b> is inherited from the library (10s) and
 * has no property here. {@code standardCircuitBreakerConfig()} already assigns
 * it, and Resilience4j's builder <i>hard-fails</i> a second assignment
 * ("The waitIntervalFunction was configured multiple times…"), so a knob would
 * have meant abandoning the shared factory for a hand-rolled config. 10s is the
 * value console-bff wants regardless, so the library's is adopted rather than
 * forked. Recorded here because "the property that isn't there" is otherwise an
 * invisible decision.
 */
@ConfigurationProperties(prefix = "consolebff.resilience")
public class ResilienceProperties {

    /**
     * Master switch. {@code true} by default — the gate is the point of the
     * service's {@code integration-heavy} trait. Present as an incident escape
     * hatch (disable without a redeploy) only; a guard that ships disabled is
     * the same defect in a new costume.
     */
    private boolean enabled = true;

    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Retry retry = new Retry();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    /** Circuit-breaker knobs applied on top of the shared library's defaults. */
    public static class CircuitBreaker {

        /** Failure-rate percentage that opens the breaker. */
        private int failureRateThreshold = 50;

        /** COUNT_BASED sliding window size, in calls. */
        private int slidingWindowSize = 10;

        /** Minimum recorded calls before the failure rate is evaluated. */
        private int minimumNumberOfCalls = 5;

        /** Probe calls admitted in HALF_OPEN before the breaker re-decides. */
        private int permittedCallsInHalfOpen = 3;

        public int getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(int failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getPermittedCallsInHalfOpen() {
            return permittedCallsInHalfOpen;
        }

        public void setPermittedCallsInHalfOpen(int permittedCallsInHalfOpen) {
            this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
        }
    }

    /** Retry knobs applied on top of the shared library's defaults. */
    public static class Retry {

        /** Total attempts including the first — {@code 2} = one bounded retry. */
        private int maxAttempts = 2;

        /**
         * Exponential-random backoff base, milliseconds. The effective wait is
         * jittered ±50% by the library's {@code IntervalFunction}.
         */
        private long backoffBaseMs = 150L;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBackoffBaseMs() {
            return backoffBaseMs;
        }

        public void setBackoffBaseMs(long backoffBaseMs) {
            this.backoffBaseMs = backoffBaseMs;
        }
    }
}
