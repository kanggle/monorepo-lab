package com.kanggle.platformconsole.bff.application.composition;

import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Common fan-out engine shared by the Operator Overview and Domain Health
 * composition use-cases (TASK-PC-BE-005 L6 duplication extraction).
 *
 * <p>Hosts the {@link #COMPOSITION_TIMEOUT} composition-level deadline,
 * the fixed {@link #CARD_ORDER}, the virtual-thread {@code fanOut}
 * coordination, the per-leg latency {@link Timer} site (route-tagged),
 * the pending-leg {@code TIMEOUT} fallback, and the
 * {@link #emitErrorCounter(DomainTarget, String)} site for
 * {@code bff_fanout_errors{domain,route,code}}. Per-use-case behavior
 * (credential dispatch, finance Option (a) gating, cross-leg 401
 * collapse, etc.) lives on the calling use-case — the engine never
 * touches a credential, never reads a tenant header, and never decides
 * on a degrade-policy classification beyond what the injected
 * {@link LegErrorClassifier} dictates.
 *
 * <p><b>Behavior byte-equal invariants</b> (TASK-PC-BE-005 hard
 * constraints — must remain unchanged after extraction):
 * <ul>
 *   <li>{@link #COMPOSITION_TIMEOUT} = 5s (both routes today).</li>
 *   <li>Per-leg latency timer: name = {@code bff_fanout_latency}, tags =
 *       {@code domain=<lowercase>, route=<route-label>}.</li>
 *   <li>Per-leg error counter: name = {@code bff_fanout_errors}, tags =
 *       {@code domain=<lowercase>, route=<route-label>, code=<classification>}.</li>
 *   <li>Pending-leg fallback at composition-level timeout: emits the
 *       {@code timeout} error counter and surfaces
 *       {@code degraded / TIMEOUT}.</li>
 *   <li>Fixed leg order: {@link DomainTarget#GAP},
 *       {@link DomainTarget#WMS}, {@link DomainTarget#SCM},
 *       {@link DomainTarget#FINANCE}, {@link DomainTarget#ERP}.</li>
 * </ul>
 *
 * <p>Concurrency: each leg gets its own Java 21 virtual thread; the
 * {@link ExecutorService} is closed at the end of {@link #fanOut(String, Map)}.
 * The use-case is responsible for keeping any request-scoped state (e.g.
 * {@code @RequestScope} credential context) off the virtual threads —
 * pre-resolve on the servlet thread and pass plain values down (see
 * {@code OperatorOverviewCompositionUseCase} pattern, byte-unchanged
 * by this extraction).
 *
 * <p><b>Trace context propagation (TASK-MONO-146)</b>: {@link #fanOut} captures
 * an {@link ContextSnapshot} on the calling (servlet) thread and wraps the
 * virtual-thread executor with it, so the inbound OTel observation/trace scope
 * is restored on each leg and every outbound {@code RestClient} span continues
 * the inbound {@code trace_id} (architecture.md § Observability D7.A — the
 * unified federation trace tree). This is orthogonal to the request-scope
 * discipline above: a {@link ContextSnapshot} only carries registered
 * {@code ThreadLocalAccessor}s (observation / MDC), and {@code @RequestScope}
 * has none — so request-scoped state still does NOT leak onto the virtual
 * threads.
 */
public final class CompositionEngine {

    private static final Logger LOG = LoggerFactory.getLogger(CompositionEngine.class);

    /**
     * Composition total timeout (per § 2.4.9.1 / § 2.4.9.2 Implementation
     * guidance — both routes share 5s).
     */
    public static final Duration COMPOSITION_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Fixed leg order — § 2.4.9.1 / § 2.4.9.2 envelope schema invariant.
     */
    public static final List<DomainTarget> CARD_ORDER = List.of(
            DomainTarget.GAP,
            DomainTarget.WMS,
            DomainTarget.SCM,
            DomainTarget.FINANCE,
            DomainTarget.ERP
    );

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final String routeLabel;

    /**
     * @param meterRegistry the Micrometer registry shared with the use-case
     *                      (used for both latency timers and error counters)
     * @param tracer        the Micrometer {@link Tracer} used to open a per-leg
     *                      {@code bff.fanout.leg} span tagged {@code bff.domain}
     *                      / {@code bff.route} (architecture.md § Observability
     *                      D7.A). Pass {@link Tracer#NOOP} when tracing is not
     *                      wired (unit tests).
     * @param routeLabel    the {@code route=...} tag value used for both
     *                      {@code bff_fanout_latency} timers and
     *                      {@code bff_fanout_errors} counters (e.g.
     *                      {@code "operator-overview"} /
     *                      {@code "domain-health"})
     */
    public CompositionEngine(MeterRegistry meterRegistry, Tracer tracer, String routeLabel) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.routeLabel = routeLabel;
    }

    public String routeLabel() {
        return routeLabel;
    }

    // ------------------------------------------------------------------
    // Fan-out coordination
    // ------------------------------------------------------------------

    /**
     * Fires the supplied per-domain leg suppliers in parallel (one virtual
     * thread per leg), waits up to {@link #COMPOSITION_TIMEOUT}, then
     * resolves each result. Legs that have not completed by the
     * composition-level deadline degrade as {@code TIMEOUT} and emit the
     * {@code timeout} error counter (matches the historic
     * {@code resolve(...)} behavior in both use-cases).
     *
     * <p>The supplied {@code legBodies} map is expected to contain exactly
     * one entry per {@link DomainTarget} in {@link #CARD_ORDER}. The caller
     * is responsible for pre-resolving any request-scoped state into plain
     * values captured by each supplier's closure — the engine does not
     * touch a credential, a tenant header, or a request-scope bean.
     *
     * @param tenantIdForLogging the active tenant — used only for
     *                           composition-level timeout logging; never
     *                           passed to a leg body
     * @param legBodies          5 leg bodies keyed by {@link DomainTarget}
     * @return the assembled per-domain results in iteration-stable order
     *         (the caller picks the {@link #CARD_ORDER} sequence)
     */
    public Map<DomainTarget, CompositionLeg> fanOut(
            String tenantIdForLogging,
            Map<DomainTarget, Supplier<CompositionLeg>> legBodies) {
        // Capture the caller (servlet) thread's propagated context — notably the
        // inbound OTel observation/trace scope — so each leg's outbound RestClient
        // span continues the SAME trace_id (architecture.md § Observability D7.A:
        // "the inbound request's OTel trace context propagates to every outbound
        // leg via W3C traceparent"). A virtual thread otherwise starts with empty
        // ThreadLocals, so each leg's client observation roots a fresh trace_id
        // (TASK-MONO-145 observed per-leg fork). Only registered
        // ThreadLocalAccessors are captured (observation / MDC); @RequestScope has
        // none, so the request-scoped credential context stays OFF the virtual
        // threads — the pre-resolve-on-servlet-thread discipline (see class
        // javadoc) is preserved.
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();
        // Java 21 virtual-thread executor: each leg gets its own VT. The wrapped
        // executor restores the captured context around every submitted leg.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Executor tracedExecutor = snapshot.wrapExecutor(executor);
            Map<DomainTarget, CompletableFuture<CompositionLeg>> futures =
                    new EnumMap<>(DomainTarget.class);
            for (DomainTarget domain : CARD_ORDER) {
                Supplier<CompositionLeg> body = legBodies.get(domain);
                if (body == null) {
                    throw new IllegalStateException(
                            "CompositionEngine.fanOut: missing leg body for " + domain);
                }
                futures.put(domain, CompletableFuture.supplyAsync(body, tracedExecutor));
            }

            CompletableFuture<Void> all = CompletableFuture.allOf(
                    futures.values().toArray(new CompletableFuture[0]));

            try {
                all.get(COMPOSITION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                // Composition-level timeout: gather what we have; missing legs degrade.
                LOG.warn("Composition-level timeout after {}ms — pending legs degraded as TIMEOUT "
                                + "(route={}, tenant={})",
                        COMPOSITION_TIMEOUT.toMillis(), routeLabel, tenantIdForLogging);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Composition interrupted", ie);
            } catch (Exception e) {
                // allOf.get() does not throw for per-leg failures (each is captured
                // inside the future). Reaching here is unexpected harness failure.
                LOG.warn("Unexpected composition-level error (route={}): {}",
                        routeLabel, e.getMessage());
            }

            Map<DomainTarget, CompositionLeg> map = new EnumMap<>(DomainTarget.class);
            for (DomainTarget domain : CARD_ORDER) {
                map.put(domain, resolve(futures.get(domain), domain));
            }
            return map;
        }
    }

    /**
     * Resolves a leg's future to a {@link CompositionLeg}. A pending future
     * (composition-level timeout) emits the {@code timeout} error counter
     * and surfaces {@code degraded / TIMEOUT} (byte-equal with the
     * historic per-use-case {@code resolve(...)} method).
     */
    private CompositionLeg resolve(CompletableFuture<CompositionLeg> f, DomainTarget domain) {
        if (f.isDone() && !f.isCompletedExceptionally()) {
            try {
                CompositionLeg r = f.getNow(null);
                if (r != null) {
                    return r;
                }
            } catch (Exception ignored) { /* fall through to degraded */ }
        }
        emitErrorCounter(domain, "timeout");
        return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "TIMEOUT"));
    }

    // ------------------------------------------------------------------
    // Per-leg timing + error classification
    // ------------------------------------------------------------------

    /**
     * Wraps the leg invocation with the per-leg latency {@link Timer} site, a
     * per-leg {@code bff.fanout.leg} trace span tagged {@code bff.domain} /
     * {@code bff.route} (architecture.md § Observability D7.A — per-domain
     * attribution in the trace UI), and delegates exception classification to
     * the supplied {@link LegErrorClassifier}. The latency timer name + tags are
     * the historic per-use-case shape: {@code bff_fanout_latency{domain,route}}.
     *
     * @param domain     the leg's domain target
     * @param call       the leg body (typically a closure that calls the
     *                   narrow read port and wraps the result into
     *                   {@link CompositionLeg#ok(LegOutcome, Object)})
     * @param classifier the per-use-case classifier that maps exceptions
     *                   into the right {@link CompositionLeg} shape
     */
    public CompositionLeg time(DomainTarget domain,
                                Supplier<CompositionLeg> call,
                                LegErrorClassifier classifier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer timer = meterRegistry.timer("bff_fanout_latency",
                "domain", lowercase(domain),
                "route", routeLabel);
        // Per-outbound-leg trace span carrying bff.domain / bff.route attributes
        // for per-domain attribution in the trace UI (architecture.md
        // § Observability D7.A). Child of the inbound server span (propagated
        // onto this virtual thread by fanOut, TASK-MONO-146) and parent of the
        // outbound RestClient client span. A Tracer span (not an Observation) is
        // used deliberately so NO 4th metric family is introduced — the explicit
        // bff_fanout_latency timer remains the sole latency metric.
        Span legSpan = tracer.nextSpan().name("bff.fanout.leg")
                .tag("bff.domain", lowercase(domain))
                .tag("bff.route", routeLabel)
                .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(legSpan)) {
            CompositionLeg r = call.get();
            sample.stop(timer);
            return r;
        } catch (RuntimeException e) {
            sample.stop(timer);
            return classifier.classify(domain, e);
        } finally {
            legSpan.end();
        }
    }

    // ------------------------------------------------------------------
    // Metric helpers (exposed so classifiers can attribute the right code)
    // ------------------------------------------------------------------

    /**
     * Increments the per-leg error counter
     * {@code bff_fanout_errors{domain,route,code}} for the configured
     * {@link #routeLabel}. The {@code code} tag value MUST be one of the
     * historic classifications ({@code missing_prerequisite},
     * {@code tenant_forbidden}, {@code permission_denied}, {@code 5xx},
     * {@code timeout}) — Prometheus dashboards depend on this stability.
     */
    public void emitErrorCounter(DomainTarget domain, String code) {
        meterRegistry.counter("bff_fanout_errors",
                "domain", lowercase(domain),
                "route", routeLabel,
                "code", code).increment();
    }

    /**
     * Increments the per-dashboard per-leg degrade counter
     * {@code bff_aggregation_degrade_count{dashboard,degraded_domain}}.
     * The {@code dashboard} tag matches the historic per-use-case
     * {@code DASHBOARD_LABEL} (today identical to {@link #routeLabel}).
     */
    public void emitAggregationDegradeCounter(String dashboardLabel, DomainTarget degradedDomain) {
        meterRegistry.counter("bff_aggregation_degrade_count",
                "dashboard", dashboardLabel,
                "degraded_domain", lowercase(degradedDomain))
                .increment();
    }

    /**
     * Lowercase the enum name for tag values — historic per-use-case helper,
     * hoisted into the engine for reuse.
     */
    public static String lowercase(DomainTarget domain) {
        return domain.name().toLowerCase();
    }
}
