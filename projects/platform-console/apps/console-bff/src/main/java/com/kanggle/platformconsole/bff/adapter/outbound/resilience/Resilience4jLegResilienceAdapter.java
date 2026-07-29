package com.kanggle.platformconsole.bff.adapter.outbound.resilience;

import com.example.common.resilience.ResilienceClientFactory;
import com.kanggle.platformconsole.bff.application.port.outbound.LegResiliencePort;
import com.kanggle.platformconsole.bff.domain.composition.CircuitOpenException;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.infrastructure.config.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resilience4j implementation of {@link LegResiliencePort} — the per-leg
 * circuit-breaker + bounded retry that architecture.md § Resilience (D5.A) and
 * {@code console-integration-contract.md} §§ 2.4.9 / 2.4.9.1 / 2.4.9.2 specify
 * (TASK-PC-BE-015).
 *
 * <h2>One gate per {@code (domain, route)}</h2>
 * Gates are created lazily and cached in a {@link ConcurrentHashMap} keyed
 * {@code "<domain>:<route>"}. {@link DomainTarget} is a closed 6-value enum and
 * the route labels are three compile-time constants, so the map is bounded at 18
 * entries — no eviction policy is needed. Independent instances are exactly what
 * gives the two required isolation axes:
 * <ul>
 *   <li>{@code (wms, operator-overview)} tripping leaves
 *       {@code (scm, operator-overview)} closed — <i>"a wms outage does not open
 *       the breaker for scm"</i>;</li>
 *   <li>{@code (wms, operator-overview)} tripping leaves
 *       {@code (wms, domain-health)} closed — <i>"sibling circuit instance …
 *       independent state, so one dashboard's circuit trip does not bleed into
 *       the other"</i> (§ 2.4.9.2).</li>
 * </ul>
 *
 * <h2>Adoption of the shared factory, unforked</h2>
 * Both primitives come from {@code libs/java-common}'s
 * {@link ResilienceClientFactory} through its <b>customizer overloads</b>, so the
 * shared failure-classification posture is inherited rather than restated — in
 * particular {@code ignoreExceptions(HttpClientErrorException.class)} on both the
 * breaker and the retry. That is load-bearing here: a per-leg {@code 403
 * TENANT_FORBIDDEN} and a cross-leg {@code 401} are authorization outcomes the
 * composition renders per-card, and neither must be breaker fuel nor be retried.
 * 5xx ({@code HttpServerErrorException}) and transport faults
 * ({@code ResourceAccessException}) are siblings, not subtypes, so they still
 * count and still trip the breaker.
 *
 * <h2>Decoration order</h2>
 * Circuit breaker <b>outermost</b>, retry inner — the order
 * {@code iam-platform/.../AuthServiceClient} established. One dashboard request
 * therefore records exactly <b>one</b> breaker call, not one per attempt, so
 * {@code minimumNumberOfCalls} counts operator-visible failures rather than
 * retry attempts.
 */
@Component
public class Resilience4jLegResilienceAdapter implements LegResiliencePort {

    private static final Logger LOG =
            LoggerFactory.getLogger(Resilience4jLegResilienceAdapter.class);

    /**
     * Additive circuit-state gauge ({@code rules/traits/integration-heavy.md}
     * § Interaction with Common Rules — "circuit 상태 변경" must be observable).
     * Value = {@code CircuitBreaker.State#getOrder()} (CLOSED=0, OPEN=1,
     * HALF_OPEN=2). Deliberately a <b>state</b> gauge, not a timing metric: the
     * three mandatory BFF metric families keep their exact names and tags, and
     * {@code bff_fanout_latency} remains the sole latency metric
     * ({@code CompositionEngine} javadoc).
     */
    static final String CIRCUIT_STATE_GAUGE = "bff_circuit_breaker_state";

    private final ResilienceProperties properties;
    private final MeterRegistry meterRegistry;
    private final Map<String, Gate> gates = new ConcurrentHashMap<>();

    public Resilience4jLegResilienceAdapter(ResilienceProperties properties,
                                            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <T> T execute(DomainTarget domain, String route, Supplier<T> call) {
        if (!properties.isEnabled()) {
            // Incident escape hatch (consolebff.resilience.enabled=false).
            // Defaults to enabled — see ResilienceProperties.
            return call.get();
        }
        Gate gate = gates.computeIfAbsent(key(domain, route),
                k -> newGate(k, domain, route));
        Supplier<T> retrying = Retry.decorateSupplier(gate.retry(), call);
        try {
            return gate.breaker().executeSupplier(retrying);
        } catch (CallNotPermittedException e) {
            // Translate at the adapter boundary so nothing above infrastructure
            // imports io.github.resilience4j (Hexagonal / trait I7).
            throw new CircuitOpenException(domain, route, e);
        }
    }

    /**
     * Resets every gate to CLOSED and drops the recorded call window.
     *
     * <p>Exists for integration tests: breaker state is process-global and
     * outlives a test method, so failures driven by one test would otherwise
     * silently put a later, unrelated test on the fail-fast path — a confident
     * wrong assertion. JUnit method order is not part of the contract, so the
     * suite resets in {@code @BeforeEach} rather than relying on ordering.
     */
    public void reset() {
        gates.values().forEach(g -> g.breaker().reset());
    }

    // ------------------------------------------------------------------
    // Gate construction
    // ------------------------------------------------------------------

    private Gate newGate(String key, DomainTarget domain, String route) {
        ResilienceProperties.CircuitBreaker cb = properties.getCircuitBreaker();
        CircuitBreaker breaker = ResilienceClientFactory.buildCircuitBreaker(key, builder -> builder
                .failureRateThreshold(cb.getFailureRateThreshold())
                // COUNT_BASED, not the library's TIME_BASED default — see
                // ResilienceProperties javadoc for why a per-dashboard-load leg
                // can never satisfy a 10s time window.
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.getSlidingWindowSize())
                .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
                // waitDurationInOpenState is deliberately NOT restated here.
                // ResilienceClientFactory.standardCircuitBreakerConfig() already
                // sets it (10s), and Resilience4j's builder hard-fails a second
                // assignment ("The waitIntervalFunction was configured multiple
                // times…"). 10s is the value console-bff wants anyway, so the
                // library's is inherited rather than forked. See
                // ResilienceProperties § "Settings intentionally NOT exposed".
                .permittedNumberOfCallsInHalfOpenState(cb.getPermittedCallsInHalfOpen()));

        breaker.getEventPublisher().onStateTransition(event -> LOG.warn(
                "Leg circuit breaker (domain={}, route={}) {} -> {}",
                lowercase(domain), route,
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()));

        ResilienceProperties.Retry rp = properties.getRetry();
        Retry retry = ResilienceClientFactory.buildRetry(key, builder -> builder
                .maxAttempts(rp.getMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        Duration.ofMillis(rp.getBackoffBaseMs()))));

        Gauge.builder(CIRCUIT_STATE_GAUGE, breaker, b -> b.getState().getOrder())
                .description("Per-leg circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .tag("domain", lowercase(domain))
                .tag("route", route)
                .register(meterRegistry);

        return new Gate(breaker, retry);
    }

    static String key(DomainTarget domain, String route) {
        return lowercase(domain) + ":" + route;
    }

    private static String lowercase(DomainTarget domain) {
        return domain.name().toLowerCase(Locale.ROOT);
    }

    /** The circuit-breaker + retry pair guarding one {@code (domain, route)} leg. */
    private record Gate(CircuitBreaker breaker, Retry retry) {
    }
}
