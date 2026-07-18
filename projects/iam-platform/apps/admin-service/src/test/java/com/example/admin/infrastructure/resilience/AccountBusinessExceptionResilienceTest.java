package com.example.admin.infrastructure.resilience;

import com.example.admin.application.exception.AccountBusinessException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.OrgNodeInvariantViolationException;
import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.SubscriptionAlreadyExistsException;
import com.example.admin.application.exception.SubscriptionNotFoundException;
import com.example.admin.application.exception.SubscriptionTransitionInvalidException;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-516 — a burst of account-service <em>business</em> responses (4xx conflicts /
 * not-found / 422 invariant) must NOT trip the admin&rarr;account circuit breaker. Before this fix
 * the typed business exceptions extended {@link RuntimeException} directly and were absent from the
 * {@code accountService} {@code ignore-exceptions}, so e.g. the federation-E2E defensive-baseline
 * SUSPEND storm (repeated SUSPEND on an already-suspended subscription &rarr; 409
 * {@code SUBSCRIPTION_TRANSITION_INVALID}) opened the circuit and the next legitimate call got
 * {@code CIRCUIT_OPEN} 503.
 *
 * <p>Two guards: (1) the resilience mechanism — ignoring the {@link AccountBusinessException} base
 * covers every concrete subtype while genuine {@link DownstreamFailureException} faults still open
 * the circuit; (2) the real {@code application.yml} artifact actually lists the base in both the
 * retry and circuit-breaker {@code ignore-exceptions} (the config drift guard).
 */
class AccountBusinessExceptionResilienceTest {

    private static final String BASE_FQN =
            "com.example.admin.application.exception.AccountBusinessException";

    /** Mirror {@code application.yml} accountService circuit breaker (see the artifact guard below). */
    private static CircuitBreaker newAccountCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .ignoreExceptions(AccountBusinessException.class)
                .build();
        return CircuitBreaker.of("accountService-test", config);
    }

    @Test
    @DisplayName("business 409/404/422 responses do NOT trip the circuit (and a later call is still permitted)")
    void businessExceptions_doNotTripTheCircuit() {
        CircuitBreaker cb = newAccountCircuitBreaker();

        // A storm of business conflicts — well past minimum-number-of-calls(5) at a 100% "failure" rate
        // if they were counted. They are ignored, so the window records nothing.
        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> cb.executeSupplier(() -> {
                throw new SubscriptionTransitionInvalidException("409 SUBSCRIPTION_TRANSITION_INVALID");
            })).isInstanceOf(SubscriptionTransitionInvalidException.class);
        }

        assertThat(cb.getState())
                .as("business conflicts must not open the circuit")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // The next legitimate call is permitted — no CIRCUIT_OPEN.
        assertThat(cb.executeSupplier(() -> "ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("genuine downstream faults (5xx) still trip the circuit — ignore is not over-broad")
    void realDownstreamFaults_stillTripTheCircuit() {
        CircuitBreaker cb = newAccountCircuitBreaker();

        for (int i = 0; i < 12; i++) {
            try {
                cb.executeSupplier(() -> {
                    throw new DownstreamFailureException("account-service error 503");
                });
            } catch (RuntimeException ignored) {
                // DownstreamFailureException while closed; CallNotPermittedException once open.
            }
        }

        assertThat(cb.getState())
                .as("real downstream faults must still open the circuit")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("every account business exception extends the ignored base (subtype coverage guard)")
    void allAccountBusinessExceptions_extendTheIgnoredBase() {
        List<Class<?>> businessExceptions = List.of(
                SubscriptionTransitionInvalidException.class,
                SubscriptionAlreadyExistsException.class,
                SubscriptionNotFoundException.class,
                TenantAlreadyExistsException.class,
                TenantNotFoundException.class,
                OrgNodeInvariantViolationException.class,
                OrgNodeNotFoundException.class);

        for (Class<?> ex : businessExceptions) {
            assertThat(AccountBusinessException.class.isAssignableFrom(ex))
                    .as("%s must extend AccountBusinessException so the circuit-breaker ignore covers it", ex.getSimpleName())
                    .isTrue();
        }

        // Guard the sibling design decision: the base must NOT be a DownstreamFailureException,
        // else the existing catch(DownstreamFailureException) fault paths would swallow business responses.
        assertThat(DownstreamFailureException.class.isAssignableFrom(AccountBusinessException.class))
                .as("AccountBusinessException must stay a sibling of DownstreamFailureException, not a subtype")
                .isFalse();
    }

    @Test
    @DisplayName("application.yml lists AccountBusinessException in accountService retry + circuit-breaker ignore-exceptions")
    void applicationYml_ignoresAccountBusinessException() throws Exception {
        Map<String, Object> resilience4j = loadResilience4jConfig();

        assertThat(ignoreExceptions(resilience4j, "retry"))
                .as("accountService retry must ignore business exceptions")
                .contains(BASE_FQN);
        assertThat(ignoreExceptions(resilience4j, "circuitbreaker"))
                .as("accountService circuit breaker must ignore business exceptions")
                .contains(BASE_FQN);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadResilience4jConfig() throws Exception {
        try (InputStream in = AccountBusinessExceptionResilienceTest.class
                .getResourceAsStream("/application.yml")) {
            assertThat(in).as("admin-service application.yml on the classpath").isNotNull();
            for (Object document : new Yaml().loadAll(in)) {
                if (document instanceof Map<?, ?> map && map.containsKey("resilience4j")) {
                    return (Map<String, Object>) map.get("resilience4j");
                }
            }
        }
        throw new AssertionError("no resilience4j section found in application.yml");
    }

    @SuppressWarnings("unchecked")
    private static List<String> ignoreExceptions(Map<String, Object> resilience4j, String type) {
        Map<String, Object> instances =
                (Map<String, Object>) ((Map<String, Object>) resilience4j.get(type)).get("instances");
        Map<String, Object> accountService = (Map<String, Object>) instances.get("accountService");
        return (List<String>) accountService.get("ignore-exceptions");
    }
}
