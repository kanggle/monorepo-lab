package com.kanggle.platformconsole.bff.adapter.outbound.resilience;

import com.kanggle.platformconsole.bff.domain.composition.CircuitOpenException;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.infrastructure.config.ResilienceProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failure-injection unit test for the per-leg resilience gate (TASK-PC-BE-015).
 *
 * <p>Mirrors {@code libs/java-common}'s {@code ResilienceClientFactoryTest}
 * shape — drive real failures through the <em>actual</em> production object
 * rather than a re-implemented config, so the assertions guard what ships.
 *
 * <p>The scenarios are the ones a circuit breaker only reveals under repeated
 * failure, and they are the ticket's AC-2/3/4/7/8/E8:
 * <ul>
 *   <li>N consecutive 5xx on one {@code (domain, route)} key OPENs it;</li>
 *   <li>once OPEN the leg body is <b>not invoked</b> and the call fails fast
 *       with {@link CircuitOpenException};</li>
 *   <li>isolation on <b>both</b> axes — a different domain on the same route,
 *       and the same domain on a different route, both stay CLOSED;</li>
 *   <li>a 4xx burst is neither retried nor counted toward the failure rate;</li>
 *   <li>the retry actually retries, and its budget fits the composition
 *       deadline.</li>
 * </ul>
 */
class Resilience4jLegResilienceAdapterTest {

    private static final String OVERVIEW = "operator-overview";
    private static final String HEALTH = "domain-health";

    private SimpleMeterRegistry meterRegistry;
    private ResilienceProperties properties;
    private Resilience4jLegResilienceAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new ResilienceProperties();
        // Retry off for the breaker scenarios: with retry on, one logical call
        // still records ONE breaker call (CB is outermost), but the leg body
        // would be invoked twice per drive and the invocation-count assertions
        // would be measuring the retry rather than the breaker. Retry has its
        // own dedicated test below.
        properties.getRetry().setMaxAttempts(1);
        adapter = new Resilience4jLegResilienceAdapter(properties, meterRegistry);
    }

    // ------------------------------------------------------------------
    // AC-2 + AC-3 — the breaker opens, then fails fast without the network
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-2/AC-3: 5 consecutive 5xx on (WMS, operator-overview) opens the gate; the next call fails fast with CircuitOpenException and never invokes the leg body")
    void repeated_server_errors_open_the_gate_then_fail_fast_without_calling_the_body() {
        AtomicInteger invocations = new AtomicInteger();

        // minimumNumberOfCalls = 5, failureRateThreshold = 50% ⇒ 5 straight
        // failures is the smallest burst that can trip it.
        drive5xx(DomainTarget.WMS, OVERVIEW, invocations,
                properties.getCircuitBreaker().getMinimumNumberOfCalls());

        assertThat(invocations.get())
                .as("every pre-open call must actually reach the leg body")
                .isEqualTo(5);

        int invocationsBeforeFailFast = invocations.get();

        assertThatThrownBy(() -> adapter.execute(DomainTarget.WMS, OVERVIEW, invocations::incrementAndGet))
                .as("an OPEN breaker must reject, not execute")
                .isInstanceOf(CircuitOpenException.class)
                .hasMessageContaining("WMS")
                .hasMessageContaining(OVERVIEW);

        // The headline property: fail-fast means NO outbound work at all — not a
        // fast failure after the call, but no call.
        assertThat(invocations.get())
                .as("the leg body must NOT run while the breaker is OPEN")
                .isEqualTo(invocationsBeforeFailFast);
    }

    @Test
    @DisplayName("AC-3: a transport failure (connect refused / read timeout) counts toward the failure rate exactly like a 5xx")
    void transport_failures_also_open_the_gate() {
        AtomicInteger invocations = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            try {
                adapter.execute(DomainTarget.SCM, OVERVIEW, () -> {
                    invocations.incrementAndGet();
                    throw new ResourceAccessException("connect refused",
                            new IOException("Connection refused"));
                });
            } catch (RuntimeException expected) {
                // propagated pre-open
            }
        }

        assertThatThrownBy(() -> adapter.execute(DomainTarget.SCM, OVERVIEW, () -> "unreachable"))
                .isInstanceOf(CircuitOpenException.class);
    }

    // ------------------------------------------------------------------
    // AC-4 — isolation on BOTH axes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-4(a): a wms outage does not open the breaker for scm — sibling domain on the same route stays CLOSED and still executes")
    void open_gate_on_one_domain_leaves_a_sibling_domain_on_the_same_route_closed() {
        drive5xx(DomainTarget.WMS, OVERVIEW, new AtomicInteger(), 5);

        // wms is fail-fast …
        assertThatThrownBy(() -> adapter.execute(DomainTarget.WMS, OVERVIEW, () -> "x"))
                .isInstanceOf(CircuitOpenException.class);

        // … while scm on the SAME route is untouched.
        AtomicInteger scmInvocations = new AtomicInteger();
        String result = adapter.execute(DomainTarget.SCM, OVERVIEW, () -> {
            scmInvocations.incrementAndGet();
            return "scm-ok";
        });
        assertThat(result).isEqualTo("scm-ok");
        assertThat(scmInvocations.get())
                .as("the unrelated domain's leg body must still run")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-4(b): (wms, operator-overview) and (wms, domain-health) are sibling instances — one dashboard's trip does not bleed into the other (contract § 2.4.9.2)")
    void open_gate_on_one_route_leaves_the_same_domain_on_another_route_closed() {
        drive5xx(DomainTarget.WMS, OVERVIEW, new AtomicInteger(), 5);

        assertThatThrownBy(() -> adapter.execute(DomainTarget.WMS, OVERVIEW, () -> "x"))
                .isInstanceOf(CircuitOpenException.class);

        // Same DOMAIN, different ROUTE. A domain-keyed-only breaker would blank
        // the Domain Health card too — that is the defect this asserts against.
        AtomicInteger healthInvocations = new AtomicInteger();
        String result = adapter.execute(DomainTarget.WMS, HEALTH, () -> {
            healthInvocations.incrementAndGet();
            return "health-ok";
        });
        assertThat(result).isEqualTo("health-ok");
        assertThat(healthInvocations.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // AC-7 — 4xx is not breaker fuel and is never retried
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-7: a 10x 403/404 burst leaves the gate CLOSED (4xx is a contract failure, not an availability failure) — inherited from the shared factory, pinned here")
    void client_errors_do_not_open_the_gate() {
        AtomicInteger invocations = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            try {
                adapter.execute(DomainTarget.IAM, OVERVIEW, () -> {
                    invocations.incrementAndGet();
                    throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
                });
            } catch (HttpClientErrorException expected) {
                // propagated unchanged so the use-case classifier can render
                // the per-card `forbidden` outcome
            }
        }

        assertThat(invocations.get()).isEqualTo(10);

        // Still executing — 10 straight 4xx at double the minimum call count did
        // not trip it. A regression here would let one mis-scoped operator's 403s
        // open a real breaker for every other operator.
        assertThat(adapter.execute(DomainTarget.IAM, OVERVIEW, () -> "still-closed"))
                .isEqualTo("still-closed");
    }

    @Test
    @DisplayName("AC-7: a 4xx is not retried even with the retry budget enabled (trait I3 — never retry a client error)")
    void client_errors_are_not_retried() {
        ResilienceProperties withRetry = new ResilienceProperties();
        withRetry.getRetry().setBackoffBaseMs(1L);
        Resilience4jLegResilienceAdapter retrying =
                new Resilience4jLegResilienceAdapter(withRetry, new SimpleMeterRegistry());

        AtomicInteger invocations = new AtomicInteger();
        assertThatThrownBy(() -> retrying.execute(DomainTarget.ERP, OVERVIEW, () -> {
            invocations.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        })).isInstanceOf(HttpClientErrorException.class);

        assertThat(invocations.get())
                .as("maxAttempts is 2, but a 4xx must be terminal on the first attempt")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Retry actually retries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("retry: a transient 5xx followed by a success returns the success — the leg body ran twice")
    void transient_failure_is_retried_then_succeeds() {
        ResilienceProperties withRetry = new ResilienceProperties();
        withRetry.getRetry().setBackoffBaseMs(1L);
        Resilience4jLegResilienceAdapter retrying =
                new Resilience4jLegResilienceAdapter(withRetry, new SimpleMeterRegistry());

        AtomicInteger invocations = new AtomicInteger();
        String result = retrying.execute(DomainTarget.FINANCE, OVERVIEW, () -> {
            if (invocations.incrementAndGet() == 1) {
                throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(invocations.get()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // AC-8 — the retry budget must fit inside the composition deadline
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-8: attempts x per-leg-timeout + backoff stays under CompositionEngine.COMPOSITION_TIMEOUT — asserted on the shipped defaults, not commented")
    void retry_budget_fits_inside_the_composition_deadline() {
        ResilienceProperties defaults = new ResilienceProperties();
        long perLegTimeoutMs = 2_000L;          // RestClientConfig.PER_LEG_TIMEOUT
        long compositionTimeoutMs = 5_000L;     // CompositionEngine.COMPOSITION_TIMEOUT

        int attempts = defaults.getRetry().getMaxAttempts();
        // IntervalFunction.ofExponentialRandomBackoff jitters +-50% around
        // base * multiplier^n; with 1 retry the single wait is bounded by
        // base * 2 * 1.5 in the worst case.
        long worstCaseBackoffMs = Math.round(defaults.getRetry().getBackoffBaseMs() * 2 * 1.5);
        long worstCaseLegMs = attempts * perLegTimeoutMs + (attempts - 1) * worstCaseBackoffMs;

        assertThat(worstCaseLegMs)
                .as("a leg that exhausts its retry budget must still degrade as a LEG failure, "
                        + "not push the whole composition past its 5s deadline "
                        + "(worst case = %dms)", worstCaseLegMs)
                .isLessThan(compositionTimeoutMs);
    }

    // ------------------------------------------------------------------
    // E8 — the escape hatch defaults to ON
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E8: the gate is ENABLED by default — a guard that ships disabled is the same defect in a new costume")
    void gate_is_enabled_by_default() {
        assertThat(new ResilienceProperties().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("E8: consolebff.resilience.enabled=false degrades execute(...) to a plain call — no breaker, no retry")
    void disabled_gate_is_pass_through() {
        ResilienceProperties disabled = new ResilienceProperties();
        disabled.setEnabled(false);
        Resilience4jLegResilienceAdapter off =
                new Resilience4jLegResilienceAdapter(disabled, new SimpleMeterRegistry());

        AtomicInteger invocations = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            try {
                off.execute(DomainTarget.WMS, OVERVIEW, () -> {
                    invocations.incrementAndGet();
                    throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
                });
            } catch (HttpServerErrorException expected) {
                // propagated
            }
        }
        // No breaker ⇒ every one of the 10 calls reached the body (an enabled
        // gate would have stopped invoking after the 5th).
        assertThat(invocations.get()).isEqualTo(10);
        assertThat(off.execute(DomainTarget.WMS, OVERVIEW, () -> "ok")).isEqualTo("ok");
    }

    // ------------------------------------------------------------------
    // Observability + test hygiene
    // ------------------------------------------------------------------

    @Test
    @DisplayName("state gauge: bff_circuit_breaker_state{domain,route} is registered per gate and tracks the transition to OPEN")
    void circuit_state_gauge_is_registered_and_tracks_state() {
        adapter.execute(DomainTarget.ERP, HEALTH, () -> "ok");

        Gauge gauge = meterRegistry.find(Resilience4jLegResilienceAdapter.CIRCUIT_STATE_GAUGE)
                .tag("domain", "erp")
                .tag("route", HEALTH)
                .gauge();
        assertThat(gauge).as("one state gauge per (domain, route) gate").isNotNull();
        assertThat(gauge.value()).as("CLOSED = 0").isZero();

        drive5xx(DomainTarget.ERP, HEALTH, new AtomicInteger(), 5);

        assertThat(gauge.value()).as("OPEN = 1").isEqualTo(1.0d);
    }

    @Test
    @DisplayName("reset(): re-CLOSEs every gate so breaker state cannot leak from one test method into the next (F5)")
    void reset_reCloses_every_gate() {
        drive5xx(DomainTarget.WMS, OVERVIEW, new AtomicInteger(), 5);
        assertThatThrownBy(() -> adapter.execute(DomainTarget.WMS, OVERVIEW, () -> "x"))
                .isInstanceOf(CircuitOpenException.class);

        adapter.reset();

        assertThat(adapter.execute(DomainTarget.WMS, OVERVIEW, () -> "reopened"))
                .isEqualTo("reopened");
    }

    // ------------------------------------------------------------------

    private void drive5xx(DomainTarget domain, String route, AtomicInteger counter, int times) {
        for (int i = 0; i < times; i++) {
            try {
                adapter.execute(domain, route, () -> {
                    counter.incrementAndGet();
                    throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
                });
            } catch (RuntimeException expected) {
                // expected — the propagated 5xx pre-open, or CircuitOpenException
                // once OPEN. Both are fine for driving the window.
            }
        }
    }
}
