package com.kanggle.platformconsole.bff.application.composition;

import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link CompositionEngine} (TASK-PC-BE-005 new harness).
 *
 * <p>Covers the three load-bearing engine behaviors that both use-cases
 * (Operator Overview + Domain Health) inherit byte-equal from the
 * historic in-line fan-out skeleton:
 *
 * <ul>
 *   <li><b>all-success</b> — every leg supplier returns ok ⇒ engine
 *       returns the supplied results keyed by domain in the fixed
 *       {@code DOMAINS} test fixture order; every leg's latency timer
 *       is registered with {@code bff_fanout_latency{domain,route}}.</li>
 *   <li><b>partial-failure</b> — one leg supplier throws ⇒ the
 *       {@link LegErrorClassifier} maps the exception to a degraded leg
 *       and emits {@code bff_fanout_errors{domain,route,code}}; other
 *       legs return ok unaffected.</li>
 *   <li><b>timeout</b> — a leg supplier blocks past
 *       {@link CompositionEngine#COMPOSITION_TIMEOUT} ⇒ engine emits the
 *       {@code timeout} error counter and degrades that leg as
 *       {@code TIMEOUT}; other legs unaffected.</li>
 * </ul>
 *
 * <p>The classifier-injected strategy is exercised in scenario (b) — the
 * engine itself never decides on a degrade classification; the strategy
 * does. This is the SAME contract both use-cases consume.
 */
class CompositionEngineTest {

    private static final String ROUTE = "operator-overview"; // arbitrary route label

    /**
     * Test-local fixture domain set — the engine is order-agnostic (TASK-MONO-241
     * decoupling; {@code CompositionEngine.CARD_ORDER} was removed as dead code,
     * TASK-PC-BE-012). Kept here only so these scenario tests have a fixed 5-domain
     * set to build {@code legBodies} maps against.
     */
    private static final List<DomainTarget> DOMAINS = List.of(
            DomainTarget.IAM,
            DomainTarget.WMS,
            DomainTarget.SCM,
            DomainTarget.FINANCE,
            DomainTarget.ERP
    );

    private SimpleMeterRegistry meterRegistry;
    private CompositionEngine engine;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        engine = new CompositionEngine(meterRegistry, Tracer.NOOP, ROUTE);
    }

    // ------------------------------------------------------------------
    // Scenario 1 — all-success
    // ------------------------------------------------------------------

    @Test
    @DisplayName("all_success: 5 leg suppliers return ok ⇒ 5 ok results in CARD_ORDER; 5 latency timers registered; 0 error counters")
    void all_success() {
        Map<DomainTarget, Supplier<CompositionLeg>> bodies = new EnumMap<>(DomainTarget.class);
        for (DomainTarget d : DOMAINS) {
            bodies.put(d, () -> engine.time(d,
                    () -> CompositionLeg.ok(LegOutcome.ok(d), Map.of("ok", true)),
                    (domain, e) -> CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"))));
        }

        Map<DomainTarget, CompositionLeg> results = engine.fanOut("tenant-1", bodies);

        assertThat(results).hasSize(5);
        for (DomainTarget d : DOMAINS) {
            assertThat(results.get(d).outcome().isOk())
                    .as("domain %s should be ok", d).isTrue();
        }
        // 5 latency timers, no error counters.
        for (DomainTarget d : DOMAINS) {
            Timer timer = meterRegistry.find("bff_fanout_latency")
                    .tag("domain", d.name().toLowerCase())
                    .tag("route", ROUTE)
                    .timer();
            assertThat(timer)
                    .as("latency timer for domain=%s route=%s", d, ROUTE)
                    .isNotNull();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
        }
        assertThat(meterRegistry.find("bff_fanout_errors").counters()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Scenario 2 — partial-failure (one leg throws; classifier emits 5xx)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("partial_failure: one leg supplier throws ⇒ engine delegates to classifier; degraded card + error counter; other legs ok")
    void partial_failure_one_leg_throws() {
        AtomicInteger classifierCalls = new AtomicInteger(0);
        LegErrorClassifier classifier = (domain, e) -> {
            classifierCalls.incrementAndGet();
            engine.emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        };

        Map<DomainTarget, Supplier<CompositionLeg>> bodies = new EnumMap<>(DomainTarget.class);
        for (DomainTarget d : DOMAINS) {
            if (d == DomainTarget.WMS) {
                bodies.put(d, () -> engine.time(d,
                        () -> { throw new RuntimeException("boom"); },
                        classifier));
            } else {
                bodies.put(d, () -> engine.time(d,
                        () -> CompositionLeg.ok(LegOutcome.ok(d), Map.of("ok", true)),
                        classifier));
            }
        }

        Map<DomainTarget, CompositionLeg> results = engine.fanOut("tenant-2", bodies);

        // WMS degraded; other 4 ok.
        assertThat(results.get(DomainTarget.WMS).outcome().isDegraded()).isTrue();
        assertThat(results.get(DomainTarget.WMS).outcome().reason()).isEqualTo("DOWNSTREAM_ERROR");
        for (DomainTarget d : List.of(DomainTarget.IAM, DomainTarget.SCM,
                DomainTarget.FINANCE, DomainTarget.ERP)) {
            assertThat(results.get(d).outcome().isOk())
                    .as("domain %s should be ok", d).isTrue();
        }

        // Classifier invoked exactly once (only on the failing leg).
        assertThat(classifierCalls.get()).isEqualTo(1);

        // Error counter for WMS / 5xx incremented exactly once.
        Counter wmsErr = meterRegistry.find("bff_fanout_errors")
                .tag("domain", "wms")
                .tag("route", ROUTE)
                .tag("code", "5xx")
                .counter();
        assertThat(wmsErr).isNotNull();
        assertThat(wmsErr.count()).isEqualTo(1.0);

        // The other 4 legs have NO error counter at any code.
        for (DomainTarget d : List.of(DomainTarget.IAM, DomainTarget.SCM,
                DomainTarget.FINANCE, DomainTarget.ERP)) {
            assertThat(meterRegistry.find("bff_fanout_errors")
                    .tag("domain", d.name().toLowerCase())
                    .tag("route", ROUTE)
                    .counters())
                    .as("no error counter expected for ok leg %s", d)
                    .isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Scenario 3 — timeout (one leg blocks past COMPOSITION_TIMEOUT)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("timeout: one leg blocks past COMPOSITION_TIMEOUT ⇒ engine emits timeout counter + degrades TIMEOUT; other legs ok")
    void timeout_one_leg_blocks() {
        // Block the SCM leg past COMPOSITION_TIMEOUT (5s) so the engine's
        // allOf.get(...) deadline trips and the pending future is resolved
        // as TIMEOUT (engine.resolve(...) pending-future fallback). The
        // small overshoot keeps the test bounded to ~5.2s.
        Map<DomainTarget, Supplier<CompositionLeg>> bodies = new EnumMap<>(DomainTarget.class);
        for (DomainTarget d : DOMAINS) {
            if (d == DomainTarget.SCM) {
                bodies.put(d, () -> {
                    try {
                        Thread.sleep(CompositionEngine.COMPOSITION_TIMEOUT.toMillis() + 200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    return CompositionLeg.ok(LegOutcome.ok(d), Map.of("ok", true));
                });
            } else {
                bodies.put(d, () -> engine.time(d,
                        () -> CompositionLeg.ok(LegOutcome.ok(d), Map.of("ok", true)),
                        (domain, e) -> CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"))));
            }
        }

        Map<DomainTarget, CompositionLeg> results = engine.fanOut("tenant-3", bodies);

        // SCM degraded as TIMEOUT (engine's pending-future fallback).
        assertThat(results.get(DomainTarget.SCM).outcome().isDegraded()).isTrue();
        assertThat(results.get(DomainTarget.SCM).outcome().reason()).isEqualTo("TIMEOUT");

        // Other 4 legs ok.
        for (DomainTarget d : List.of(DomainTarget.IAM, DomainTarget.WMS,
                DomainTarget.FINANCE, DomainTarget.ERP)) {
            assertThat(results.get(d).outcome().isOk())
                    .as("domain %s should be ok", d).isTrue();
        }

        // Timeout counter for SCM emitted by engine.resolve(...) on the pending future.
        Counter scmTimeout = meterRegistry.find("bff_fanout_errors")
                .tag("domain", "scm")
                .tag("route", ROUTE)
                .tag("code", "timeout")
                .counter();
        assertThat(scmTimeout).isNotNull();
        assertThat(scmTimeout.count()).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    // Auxiliary: aggregation degrade counter helper
    // ------------------------------------------------------------------

    @Test
    @DisplayName("emitAggregationDegradeCounter: increments bff_aggregation_degrade_count{dashboard,degraded_domain}")
    void emit_aggregation_degrade_counter() {
        engine.emitAggregationDegradeCounter("operator-overview", DomainTarget.FINANCE);
        engine.emitAggregationDegradeCounter("operator-overview", DomainTarget.FINANCE);
        engine.emitAggregationDegradeCounter("operator-overview", DomainTarget.WMS);

        Counter financeCounter = meterRegistry.find("bff_aggregation_degrade_count")
                .tag("dashboard", "operator-overview")
                .tag("degraded_domain", "finance")
                .counter();
        Counter wmsCounter = meterRegistry.find("bff_aggregation_degrade_count")
                .tag("dashboard", "operator-overview")
                .tag("degraded_domain", "wms")
                .counter();
        assertThat(financeCounter).isNotNull();
        assertThat(financeCounter.count()).isEqualTo(2.0);
        assertThat(wmsCounter).isNotNull();
        assertThat(wmsCounter.count()).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    // Scenario 5 — trace-context propagation to virtual-thread legs (MONO-146)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("context_propagation: fanOut captures the calling-thread context and restores it on every virtual-thread leg (TASK-MONO-146)")
    void fanOut_propagates_calling_thread_context_to_virtual_thread_legs() {
        // Stand-in for the inbound OTel observation scope: a calling-thread
        // ThreadLocal exposed via a registered ThreadLocalAccessor. fanOut()
        // captures a ContextSnapshot on the servlet thread and wraps the
        // virtual-thread executor, so each leg must observe this value on its
        // own VT. Without the snapshot wrap a VT starts with empty ThreadLocals
        // (value would be null), which is exactly the per-leg trace_id fork
        // TASK-MONO-145 observed.
        final String accessorKey = "test.trace.ctx";
        ThreadLocal<String> callerCtx = new ThreadLocal<>();
        ThreadLocalAccessor<String> accessor = new ThreadLocalAccessor<>() {
            @Override
            public Object key() {
                return accessorKey;
            }

            @Override
            public String getValue() {
                return callerCtx.get();
            }

            @Override
            public void setValue(String value) {
                callerCtx.set(value);
            }

            @Override
            public void setValue() {
                callerCtx.remove();
            }
        };

        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor(accessor);
        try {
            callerCtx.set("trace-abc-123");

            Map<DomainTarget, String> observed = new ConcurrentHashMap<>();
            Map<DomainTarget, Supplier<CompositionLeg>> bodies =
                    new EnumMap<>(DomainTarget.class);
            for (DomainTarget d : DOMAINS) {
                bodies.put(d, () -> {
                    // Runs on a virtual thread — the propagated context must be visible.
                    String seen = callerCtx.get();
                    observed.put(d, seen == null ? "<null>" : seen);
                    return CompositionLeg.ok(LegOutcome.ok(d), Map.of("ok", true));
                });
            }

            Map<DomainTarget, CompositionLeg> results = engine.fanOut("tenant-ctx", bodies);

            assertThat(results).hasSize(5);
            assertThat(observed).hasSize(5);
            for (DomainTarget d : DOMAINS) {
                assertThat(observed.get(d))
                        .as("leg %s must observe the propagated calling-thread context on its virtual thread", d)
                        .isEqualTo("trace-abc-123");
            }
        } finally {
            registry.removeThreadLocalAccessor(accessorKey);
            callerCtx.remove();
        }
    }
}
