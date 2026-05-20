package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.domain.composition.DegradePolicy;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Phase 7 "Domain Health Overview" composition use-case.
 *
 * <p>Fans out across all 5 backend domains in parallel (Java 21 virtual threads),
 * reading each domain's public Spring Boot {@code /actuator/health} endpoint and
 * mapping the result to a {@link LegOutcome}. The controller maps to the HTTP
 * envelope per {@code console-integration-contract.md} § 2.4.9.2.
 *
 * <p>Hard invariants (§ 2.4.9.2 + ADR-MONO-017 § 3.3 #4):
 * <ul>
 *   <li><b>No outbound credential</b> — every leg's {@code /actuator/health} is
 *       called with NO {@code Authorization} header (D4 scope clarification:
 *       D4 governs § 2.4.5/6/7/8 data-API legs only; public actuator metadata
 *       endpoints are outside D4). The
 *       {@link com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort}
 *       sealed switch is <b>never invoked</b> from this use-case.</li>
 *   <li><b>No tenant pass-through</b> — actuator endpoints are not tenant-scoped;
 *       {@code X-Tenant-Id} is NOT forwarded to any leg.</li>
 *   <li><b>All-down still emits 5 legs</b> — every leg can return non-ok
 *       simultaneously; the controller still emits HTTP 200 (D5.A; D5.B rejection).</li>
 *   <li><b>No cross-leg 401 collapse</b> — actuator endpoints are public, so a
 *       401 from any leg is itself an unexpected (producer-side actuator
 *       misconfiguration). It is mapped to {@code degraded / DOWNSTREAM_ERROR}
 *       for that card, NOT to a composition-level 401.</li>
 *   <li><b>Fixed 5-leg order</b> — {@code [gap, wms, scm, finance, erp]}.</li>
 *   <li><b>status ∈ {ok, degraded} only</b> — {@code forbidden} is never emitted
 *       on this route (no permission decision exists on a public actuator leg).</li>
 *   <li><b>Composition timeout</b> 5s total; per-leg 2s (bounded by the
 *       {@code RestClient} bean shared with the Operator Overview route).</li>
 * </ul>
 *
 * <p><b>Application-layer purity</b>: depends only on outbound ports (the 5
 * inner {@code *HealthReadPort} interfaces), domain primitives
 * ({@link LegOutcome} / {@link DegradePolicy}), and a thin observability
 * surface ({@link MeterRegistry}).
 */
@Service
public class DomainHealthCompositionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DomainHealthCompositionUseCase.class);

    /** Composition total timeout (per § 2.4.9.2 Implementation guidance, mirrors § 2.4.9.1). */
    static final Duration COMPOSITION_TIMEOUT = Duration.ofSeconds(5);

    /** Route label value for per-leg metric emission. */
    static final String ROUTE_LABEL = "domain-health";

    /** Dashboard label value for the aggregation-degrade counter. */
    static final String DASHBOARD_LABEL = "domain-health";

    /** Fixed leg order — § 2.4.9.2 envelope schema invariant. */
    public static final List<DomainTarget> CARD_ORDER = List.of(
            DomainTarget.GAP,
            DomainTarget.WMS,
            DomainTarget.SCM,
            DomainTarget.FINANCE,
            DomainTarget.ERP
    );

    private final MeterRegistry meterRegistry;
    private final GapHealthReadPort gapPort;
    private final WmsHealthReadPort wmsPort;
    private final ScmHealthReadPort scmPort;
    private final FinanceHealthReadPort financePort;
    private final ErpHealthReadPort erpPort;

    public DomainHealthCompositionUseCase(
            MeterRegistry meterRegistry,
            GapHealthReadPort gapPort,
            WmsHealthReadPort wmsPort,
            ScmHealthReadPort scmPort,
            FinanceHealthReadPort financePort,
            ErpHealthReadPort erpPort) {
        this.meterRegistry = meterRegistry;
        this.gapPort = gapPort;
        this.wmsPort = wmsPort;
        this.scmPort = scmPort;
        this.financePort = financePort;
        this.erpPort = erpPort;
    }

    /**
     * Composes the domain-health envelope by firing 5 parallel outbound legs.
     *
     * @return the fixed-order 5-leg composition result (gap, wms, scm, finance,
     *         erp); each leg carries its {@link LegOutcome} and the optional
     *         ok-payload (the producer's actuator health JSON)
     */
    public List<CompositionLeg> compose() {
        Map<DomainTarget, CompositionLeg> results = fanOut();

        // Per-leg degrade-counter emission + fixed-order assembly.
        // status ∈ {ok, degraded} only — no forbidden, no cross-leg 401 collapse.
        List<LegOutcome> outcomesForPolicy = new ArrayList<>();
        List<CompositionLeg> ordered = new ArrayList<>(CARD_ORDER.size());
        for (DomainTarget domain : CARD_ORDER) {
            CompositionLeg leg = results.get(domain);
            outcomesForPolicy.add(leg.outcome());
            ordered.add(leg);
            if (!leg.outcome().isOk()) {
                meterRegistry.counter("bff_aggregation_degrade_count",
                        "dashboard", DASHBOARD_LABEL,
                        "degraded_domain", lowercase(domain))
                        .increment();
            }
        }

        if (DegradePolicy.isAllDown(outcomesForPolicy)) {
            LOG.warn("Domain-health composition: all 5 legs non-ok (still emitting 200 per D5.A)");
        }
        return ordered;
    }

    // ------------------------------------------------------------------
    // Internal fan-out
    // ------------------------------------------------------------------

    private Map<DomainTarget, CompositionLeg> fanOut() {
        // Java 21 virtual-thread executor: each leg gets its own VT.
        // No credential pre-resolve — each leg fires a credential-less GET.
        // The @RequestScope bean OperatorCredentialContext is NOT touched here.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<CompositionLeg> gapFuture = supply(executor, this::callGap);
            CompletableFuture<CompositionLeg> wmsFuture = supply(executor, this::callWms);
            CompletableFuture<CompositionLeg> scmFuture = supply(executor, this::callScm);
            CompletableFuture<CompositionLeg> financeFuture = supply(executor, this::callFinance);
            CompletableFuture<CompositionLeg> erpFuture = supply(executor, this::callErp);

            CompletableFuture<Void> all = CompletableFuture.allOf(
                    gapFuture, wmsFuture, scmFuture, financeFuture, erpFuture);

            try {
                all.get(COMPOSITION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                LOG.warn("Composition-level timeout after {}ms — pending legs degraded as TIMEOUT",
                        COMPOSITION_TIMEOUT.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Composition interrupted", ie);
            } catch (Exception e) {
                LOG.warn("Unexpected composition-level error: {}", e.getMessage());
            }

            Map<DomainTarget, CompositionLeg> map = new EnumMap<>(DomainTarget.class);
            map.put(DomainTarget.GAP, resolve(gapFuture, DomainTarget.GAP));
            map.put(DomainTarget.WMS, resolve(wmsFuture, DomainTarget.WMS));
            map.put(DomainTarget.SCM, resolve(scmFuture, DomainTarget.SCM));
            map.put(DomainTarget.FINANCE, resolve(financeFuture, DomainTarget.FINANCE));
            map.put(DomainTarget.ERP, resolve(erpFuture, DomainTarget.ERP));
            return map;
        }
    }

    private static CompletableFuture<CompositionLeg> supply(ExecutorService executor,
                                                            Supplier<CompositionLeg> body) {
        return CompletableFuture.supplyAsync(body, executor);
    }

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
    // Per-leg invocation (each runs in its own virtual thread).
    //
    // Every method:
    //   (1) Calls the credential-less /actuator/health endpoint via its port.
    //   (2) Times the call via the per-leg latency Timer (route="domain-health").
    //   (3) Classifies failures into LegOutcome per § 2.4.9.2 observability table.
    // ------------------------------------------------------------------

    private CompositionLeg callGap() {
        return time(DomainTarget.GAP, () -> {
            Map<String, Object> data = gapPort.read();
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.GAP), data);
        });
    }

    private CompositionLeg callWms() {
        return time(DomainTarget.WMS, () -> {
            Map<String, Object> data = wmsPort.read();
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.WMS), data);
        });
    }

    private CompositionLeg callScm() {
        return time(DomainTarget.SCM, () -> {
            Map<String, Object> data = scmPort.read();
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), data);
        });
    }

    private CompositionLeg callFinance() {
        return time(DomainTarget.FINANCE, () -> {
            Map<String, Object> data = financePort.read();
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.FINANCE), data);
        });
    }

    private CompositionLeg callErp() {
        return time(DomainTarget.ERP, () -> {
            Map<String, Object> data = erpPort.read();
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), data);
        });
    }

    // ------------------------------------------------------------------
    // Timing + error classification
    //
    // status outcomes restricted to {OK, DEGRADED} — no FORBIDDEN path
    // (actuator legs cannot meaningfully return a permission decision).
    // ------------------------------------------------------------------

    private CompositionLeg time(DomainTarget domain, Supplier<CompositionLeg> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer timer = meterRegistry.timer("bff_fanout_latency",
                "domain", lowercase(domain),
                "route", ROUTE_LABEL);
        try {
            CompositionLeg r = call.get();
            sample.stop(timer);
            return r;
        } catch (HttpClientErrorException ce) {
            // 401/403/4xx/5xx from a public actuator leg — all classify as
            // DOWNSTREAM_ERROR (no permission outcome for actuator legs).
            sample.stop(timer);
            emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        } catch (ResourceAccessException rae) {
            sample.stop(timer);
            if (rae.getCause() instanceof SocketTimeoutException) {
                emitErrorCounter(domain, "timeout");
                return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "TIMEOUT"));
            }
            emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        } catch (RuntimeException e) {
            sample.stop(timer);
            emitErrorCounter(domain, "5xx");
            LOG.warn("Leg {} unexpected error: {}: {}", domain,
                    e.getClass().getSimpleName(), e.getMessage());
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        }
    }

    private void emitErrorCounter(DomainTarget domain, String code) {
        meterRegistry.counter("bff_fanout_errors",
                "domain", lowercase(domain),
                "route", ROUTE_LABEL,
                "code", code).increment();
    }

    private static String lowercase(DomainTarget domain) {
        return domain.name().toLowerCase();
    }

    // ------------------------------------------------------------------
    // Public types — carriers for the controller to consume.
    // ------------------------------------------------------------------

    /**
     * Composition leg result — pairs the {@link LegOutcome} (status + reason)
     * with the optional ok-payload (the producer's actuator health JSON). The
     * controller maps to the wire envelope.
     *
     * <p>Sibling to {@code OperatorOverviewCompositionUseCase.CompositionLeg}
     * but without an {@code unauthorized} flag — cross-leg 401 collapse is not
     * a discipline of this route (actuator legs do not share an inbound
     * credential, so a 401 from one is not a 401 for all).
     *
     * @param outcome the leg's classified outcome
     * @param data    the ok-payload (null on degraded)
     */
    public record CompositionLeg(LegOutcome outcome, Object data) {

        public static CompositionLeg ok(LegOutcome outcome, Object data) {
            return new CompositionLeg(outcome, data);
        }

        public static CompositionLeg outcomeOnly(LegOutcome outcome) {
            return new CompositionLeg(outcome, null);
        }
    }

    // ------------------------------------------------------------------
    // Narrow port interfaces — typed to keep the application layer
    // framework-free while letting Spring autowire the concrete adapters.
    //
    // No credential / tenant arguments — actuator endpoints are public
    // and not tenant-scoped (§ 2.4.9.2 hard invariants).
    // ------------------------------------------------------------------

    /** Narrow port: GAP gateway-service /actuator/health. */
    public interface GapHealthReadPort {
        Map<String, Object> read();
    }

    /** Narrow port: WMS gateway-service /actuator/health. */
    public interface WmsHealthReadPort {
        Map<String, Object> read();
    }

    /** Narrow port: SCM gateway-service /actuator/health. */
    public interface ScmHealthReadPort {
        Map<String, Object> read();
    }

    /** Narrow port: finance account-service /actuator/health (no gateway in v1). */
    public interface FinanceHealthReadPort {
        Map<String, Object> read();
    }

    /** Narrow port: erp masterdata-service /actuator/health (no gateway in v1). */
    public interface ErpHealthReadPort {
        Map<String, Object> read();
    }
}
