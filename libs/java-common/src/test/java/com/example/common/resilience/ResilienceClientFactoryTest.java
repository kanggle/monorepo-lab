package com.example.common.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the shared circuit-breaker default's failure-classification posture
 * (TASK-MONO-427). The breaker under test is built from the <em>actual</em>
 * factory method, not a re-implemented config, so the assertions guard the
 * shipped defaults directly.
 */
class ResilienceClientFactoryTest {

    /**
     * A burst of 4xx client errors far beyond {@code minimumNumberOfCalls} at a
     * 100% rate must NOT open the breaker — 4xx is a contract failure, not an
     * availability failure. The circuit stays CLOSED and a subsequent call is
     * permitted. (AC-2 CLOSED half; mutation target for AC-3.)
     */
    @Test
    void burstOfClientErrors_keepsBreakerClosed_andSubsequentCallPermitted() {
        CircuitBreaker breaker = ResilienceClientFactory.buildCircuitBreaker("cb-4xx");
        HttpClientErrorException clientError = new HttpClientErrorException(HttpStatus.BAD_REQUEST);

        // 10 client errors — double the minimumNumberOfCalls (5), 100% failure
        // rate if they were counted. They must be ignored instead.
        driveFailures(breaker, clientError, 10);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        // A subsequent successful call is permitted (no CallNotPermittedException).
        String result = breaker.executeSupplier(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    /**
     * A burst of 5xx server errors — a genuine downstream availability fault —
     * must OPEN the breaker. Confirms the 4xx ignore is not over-broad: real
     * faults still trip it, and subsequent calls fail fast. (AC-2 OPEN half.)
     */
    @Test
    void burstOfServerErrors_opensBreaker() {
        CircuitBreaker breaker = ResilienceClientFactory.buildCircuitBreaker("cb-5xx");
        HttpServerErrorException serverError = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        driveFailures(breaker, serverError, 10);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        // Once OPEN, further calls fail fast with CallNotPermittedException.
        assertThatThrownBy(() -> breaker.executeSupplier(() -> "should-not-run"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    /**
     * Edge case (task): {@code HttpServerErrorException} (5xx) is a sibling of
     * {@code RestClientResponseException}, NOT a subtype of
     * {@link HttpClientErrorException}, so ignoring 4xx does not ignore 5xx.
     */
    @Test
    void serverError_isNotSubtypeOfClientError() {
        assertThat(HttpClientErrorException.class.isAssignableFrom(HttpServerErrorException.class))
                .as("5xx must not be a subtype of HttpClientErrorException (else ignoring 4xx would swallow 5xx)")
                .isFalse();
    }

    private void driveFailures(CircuitBreaker breaker, RuntimeException error, int times) {
        for (int i = 0; i < times; i++) {
            try {
                breaker.executeSupplier(() -> {
                    throw error;
                });
            } catch (RuntimeException expected) {
                // expected — either the propagated error or, once OPEN,
                // CallNotPermittedException; both are fine for driving the window.
            }
        }
    }
}
