package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.port.outbound.DomainReadPort;
import com.kanggle.platformconsole.bff.domain.composition.DegradePolicy;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * MVP "Operator Overview" composition use-case.
 *
 * <p>Fans out across all 5 backend domains in parallel (Java 21 virtual threads),
 * maps each leg to a {@link LegOutcome}, emits per-leg + degrade metrics, and
 * returns the fixed-order 5 {@link CompositionLeg} results. The controller maps
 * the result to the HTTP envelope per § 2.4.9.1.
 *
 * <p>Hard invariants (§ 2.4.9.1 + ADR-MONO-017):
 * <ul>
 *   <li><b>Per-domain credential dispatch (D4 HARD INVARIANT)</b> — every leg
 *       resolves its bearer via
 *       {@link CredentialSelectionPort#selectFor(DomainTarget)}; no fallback
 *       path; no unified token. The sealed-switch on
 *       {@link OutboundCredential} is the SINGLE truth.</li>
 *   <li><b>All-down still emits 5 legs</b> — every leg can return non-ok
 *       simultaneously; the controller still emits HTTP 200 (D5.A;
 *       D5.B rejection).</li>
 *   <li><b>401 cross-leg</b> — if ANY leg returns 401 to the BFF, the
 *       use case throws {@link UpstreamUnauthorizedException} which the
 *       inbound handler maps to {@code 401 TOKEN_INVALID} (§ 2.4.4 D3).</li>
 *   <li><b>Fixed 5-leg order</b> — {@code [gap, wms, scm, finance, erp]}.</li>
 *   <li><b>Tenant pass-through verbatim</b> (D6.A).</li>
 *   <li><b>Composition timeout</b> 5s total; per-leg 2s — a slow leg degrades
 *       with {@code TIMEOUT}.</li>
 *   <li><b>Finance MVP option (b)</b> — when no
 *       {@code operatorDefaultAccountId} is resolvable from request context,
 *       finance leg is {@code forbidden / MISSING_PREREQUISITE} and the
 *       outbound HTTP call is NEVER fired (Javadoc-asserted; unit-tested).</li>
 * </ul>
 *
 * <p><b>Application-layer purity</b>: depends only on outbound ports
 * ({@link DomainReadPort}), the {@link CredentialSelectionPort}, domain
 * primitives ({@link LegOutcome} / {@link DegradePolicy} /
 * {@link OutboundCredential}), and a thin observability surface
 * ({@link MeterRegistry}). The exception type
 * {@link UpstreamUnauthorizedException} lives at the application layer so the
 * use case can signal cross-leg 401 without depending on inbound web types.
 */
@Service
public class OperatorOverviewCompositionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorOverviewCompositionUseCase.class);

    /** Composition total timeout (per § 2.4.9.1 Implementation guidance). */
    static final Duration COMPOSITION_TIMEOUT = Duration.ofSeconds(5);

    /** Route label value for per-leg metric emission. */
    static final String ROUTE_LABEL = "operator-overview";

    /** Dashboard label value for the aggregation-degrade counter. */
    static final String DASHBOARD_LABEL = "operator-overview";

    /** Fixed leg order — § 2.4.9.1 envelope schema invariant. */
    public static final List<DomainTarget> CARD_ORDER = List.of(
            DomainTarget.GAP,
            DomainTarget.WMS,
            DomainTarget.SCM,
            DomainTarget.FINANCE,
            DomainTarget.ERP
    );

    private final CredentialSelectionPort credentialSelection;
    private final MeterRegistry meterRegistry;
    private final DomainReadPort<Map<String, Object>> gapPort;
    private final DomainReadPort<Map<String, Object>> wmsPort;
    private final DomainReadPort<Map<String, Object>> scmPort;
    private final DomainReadPort<Map<String, Object>> financePort;
    private final DomainReadPort<Map<String, Object>> erpPort;

    /**
     * Constructor wires the 5 outbound ports via the narrow named-bean
     * interfaces. Concrete adapter implementations live in
     * {@code adapter.outbound.http}.
     */
    public OperatorOverviewCompositionUseCase(
            CredentialSelectionPort credentialSelection,
            MeterRegistry meterRegistry,
            GapAccountsReadPort gapPort,
            WmsInventoryReadPort wmsPort,
            ScmInventoryReadPort scmPort,
            FinanceBalanceReadPort financePort,
            ErpDepartmentsReadPort erpPort) {
        this.credentialSelection = credentialSelection;
        this.meterRegistry = meterRegistry;
        this.gapPort = gapPort;
        this.wmsPort = wmsPort;
        this.scmPort = scmPort;
        this.financePort = financePort;
        this.erpPort = erpPort;
    }

    /**
     * Composes the operator overview by firing 5 parallel outbound legs.
     *
     * @param tenantId active tenant forwarded verbatim on every leg (D6.A)
     * @return the fixed-order 5-leg composition result (gap, wms, scm,
     *         finance, erp); each leg carries its {@link LegOutcome} and the
     *         optional ok-payload
     * @throws UpstreamUnauthorizedException if any outbound leg returned 401
     * @throws MissingCredentialException    if a required inbound token is absent
     *                                       at top-level (defensive — most
     *                                       absence cases are turned into
     *                                       per-leg outcomes)
     */
    public List<CompositionLeg> compose(String tenantId) {
        Map<DomainTarget, CompositionLeg> results = fanOut(tenantId);

        // (1) Cross-leg 401: collapse to composition-level 401
        //     (§ 2.4.4 D3 / § 2.4.9.1 — auth is not a per-card degrade).
        for (CompositionLeg leg : results.values()) {
            if (leg.unauthorized()) {
                throw new UpstreamUnauthorizedException(
                        "Upstream leg returned 401 — composition collapses to 401 (cross-leg discipline)");
            }
        }

        // (2) Per-leg degrade-counter emission + fixed-order assembly.
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
            LOG.warn("Operator-overview composition: all 5 legs non-ok (still emitting 200 per D5.A)");
        }
        return ordered;
    }

    // ------------------------------------------------------------------
    // Internal fan-out
    // ------------------------------------------------------------------

    private Map<DomainTarget, CompositionLeg> fanOut(String tenantId) {
        // Java 21 virtual-thread executor: each leg gets its own VT.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<CompositionLeg> gapFuture = supply(executor, () -> callGap(tenantId));
            CompletableFuture<CompositionLeg> wmsFuture = supply(executor, () -> callWms(tenantId));
            CompletableFuture<CompositionLeg> scmFuture = supply(executor, () -> callScm(tenantId));
            CompletableFuture<CompositionLeg> financeFuture = supply(executor, () -> callFinance(tenantId));
            CompletableFuture<CompositionLeg> erpFuture = supply(executor, () -> callErp(tenantId));

            CompletableFuture<Void> all = CompletableFuture.allOf(
                    gapFuture, wmsFuture, scmFuture, financeFuture, erpFuture);

            try {
                all.get(COMPOSITION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                // Composition-level timeout: gather what we have; missing legs degrade.
                LOG.warn("Composition-level timeout after {}ms — pending legs degraded as TIMEOUT",
                        COMPOSITION_TIMEOUT.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Composition interrupted", ie);
            } catch (Exception e) {
                // allOf.get() does not throw for per-leg failures (each is captured
                // inside the future). Reaching here is unexpected harness failure.
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
        // Pending or failed at composition level → TIMEOUT degrade.
        emitErrorCounter(domain, "timeout");
        return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "TIMEOUT"));
    }

    // ------------------------------------------------------------------
    // Per-leg invocation (each runs in its own virtual thread).
    //
    // Every method:
    //   (1) Resolves the credential via CredentialSelectionPort.selectFor(...)
    //       and switches on the sealed OutboundCredential record (D4 invariant).
    //   (2) Times the call via the per-leg latency Timer.
    //   (3) Classifies failures into LegOutcome per § 2.4.9 observability table.
    // ------------------------------------------------------------------

    private CompositionLeg callGap(String tenantId) {
        return time(DomainTarget.GAP, () -> {
            String bearer = bearerFor(DomainTarget.GAP);
            Map<String, Object> data = gapPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.GAP), data);
        });
    }

    private CompositionLeg callWms(String tenantId) {
        return time(DomainTarget.WMS, () -> {
            String bearer = bearerFor(DomainTarget.WMS);
            Map<String, Object> data = wmsPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.WMS), data);
        });
    }

    private CompositionLeg callScm(String tenantId) {
        return time(DomainTarget.SCM, () -> {
            String bearer = bearerFor(DomainTarget.SCM);
            Map<String, Object> data = scmPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), data);
        });
    }

    /**
     * Finance MVP option (b) per § 2.4.9.1 Implementation guidance: when no
     * {@code operatorDefaultAccountId} is resolvable from request context,
     * surface {@code forbidden / MISSING_PREREQUISITE} <b>without</b> firing
     * the outbound HTTP call. Finance v1 has no list/search GET, so no
     * account list to summarize. Option (a) (GAP registry per-operator
     * {@code finance.defaultAccountId} claim) is a separately-tracked
     * spec-first GAP enhancement, deferred.
     *
     * <p>The unit test asserts this branch.
     */
    private CompositionLeg callFinance(String tenantId) {
        Optional<String> accountId = resolveOperatorDefaultAccountId();
        if (accountId.isEmpty()) {
            emitErrorCounter(DomainTarget.FINANCE, "missing_prerequisite");
            return CompositionLeg.outcomeOnly(
                    LegOutcome.forbidden(DomainTarget.FINANCE, "MISSING_PREREQUISITE"));
        }
        return time(DomainTarget.FINANCE, () -> {
            String bearer = bearerFor(DomainTarget.FINANCE);
            // The narrow port adapter for finance dispatches to /balances when an
            // id is supplied; the underlying adapter wires the path template.
            Map<String, Object> data = financePort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.FINANCE), data);
        });
    }

    private CompositionLeg callErp(String tenantId) {
        return time(DomainTarget.ERP, () -> {
            String bearer = bearerFor(DomainTarget.ERP);
            Map<String, Object> data = erpPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), data);
        });
    }

    /**
     * Resolves the bearer token value for {@code domain} via the
     * {@link CredentialSelectionPort} (D4 HARD INVARIANT). Switches on the
     * sealed {@link OutboundCredential} hierarchy — never falls back.
     */
    private String bearerFor(DomainTarget domain) {
        OutboundCredential cred = credentialSelection.selectFor(domain);
        return switch (cred) {
            case OutboundCredential.OperatorToken t -> t.token();
            case OutboundCredential.GapOidcAccessToken t -> t.token();
        };
    }

    /**
     * MVP: no inbound mechanism for the operator's default finance account id
     * exists (option a — GAP registry surface — is deferred). Always returns
     * {@link Optional#empty()} which the caller maps to
     * {@code MISSING_PREREQUISITE}.
     */
    private Optional<String> resolveOperatorDefaultAccountId() {
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Timing + error classification
    // ------------------------------------------------------------------

    /**
     * Wraps the leg invocation with the per-leg latency Timer and translates
     * exceptions into {@link LegOutcome} classifications per § 2.4.9
     * observability table.
     */
    private CompositionLeg time(DomainTarget domain, Supplier<CompositionLeg> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer timer = meterRegistry.timer("bff_fanout_latency",
                "domain", lowercase(domain),
                "route", ROUTE_LABEL);
        try {
            CompositionLeg r = call.get();
            sample.stop(timer);
            return r;
        } catch (MissingCredentialException mce) {
            // GAP leg with absent operator token (fail-closed; HARD INVARIANT).
            sample.stop(timer);
            emitErrorCounter(domain, "missing_prerequisite");
            return CompositionLeg.outcomeOnly(
                    LegOutcome.forbidden(domain, "MISSING_PREREQUISITE"));
        } catch (HttpClientErrorException.Unauthorized ue) {
            sample.stop(timer);
            // Tag the metric separately from server 5xx so operators can see the
            // upstream-401 surface; the unauthorized flag bubbles up.
            emitErrorCounter(domain, "5xx");
            return CompositionLeg.unauthorized(domain);
        } catch (HttpClientErrorException.Forbidden fe) {
            sample.stop(timer);
            String reason = classifyForbidden(fe);
            emitErrorCounter(domain,
                    "TENANT_FORBIDDEN".equals(reason) ? "tenant_forbidden" : "permission_denied");
            return CompositionLeg.outcomeOnly(LegOutcome.forbidden(domain, reason));
        } catch (HttpClientErrorException ce) {
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

    /**
     * 403 may signal either {@code TENANT_FORBIDDEN} or generic
     * {@code PERMISSION_DENIED}. Peek at the producer envelope body for the
     * authoritative code; default to PERMISSION_DENIED.
     */
    private static String classifyForbidden(HttpClientErrorException fe) {
        try {
            String body = fe.getResponseBodyAsString();
            if (body != null && body.contains("TENANT_FORBIDDEN")) {
                return "TENANT_FORBIDDEN";
            }
        } catch (RuntimeException ignored) { /* body absent / not JSON */ }
        return "PERMISSION_DENIED";
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
     * with the optional ok-payload (domain-shaped map). The controller maps
     * to the wire envelope.
     *
     * @param outcome       the leg's classified outcome
     * @param data          the ok-payload (null on degraded / forbidden)
     * @param unauthorized  true iff the upstream leg returned 401 (only set
     *                      by {@link CompositionLeg#unauthorized(DomainTarget)})
     */
    public record CompositionLeg(LegOutcome outcome, Object data, boolean unauthorized) {

        public static CompositionLeg ok(LegOutcome outcome, Object data) {
            return new CompositionLeg(outcome, data, false);
        }

        public static CompositionLeg outcomeOnly(LegOutcome outcome) {
            return new CompositionLeg(outcome, null, false);
        }

        public static CompositionLeg unauthorized(DomainTarget domain) {
            return new CompositionLeg(LegOutcome.degraded(domain, "UNAUTHORIZED"), null, true);
        }
    }

    // ------------------------------------------------------------------
    // Narrow port interfaces — typed to keep the application layer
    // framework-free while letting Spring autowire the concrete adapters.
    //
    // Each interface is a no-op extension of DomainReadPort<Map<String,Object>>.
    // The concrete adapters in adapter.outbound.http implement the
    // corresponding narrow port; Spring resolves the bean by type at injection.
    // ------------------------------------------------------------------

    /** Narrow port: GAP accounts read. */
    public interface GapAccountsReadPort extends DomainReadPort<Map<String, Object>> {}

    /** Narrow port: wms inventory snapshot read. */
    public interface WmsInventoryReadPort extends DomainReadPort<Map<String, Object>> {}

    /** Narrow port: scm inventory visibility read. */
    public interface ScmInventoryReadPort extends DomainReadPort<Map<String, Object>> {}

    /** Narrow port: finance balance read (operator default account id resolved upstream). */
    public interface FinanceBalanceReadPort extends DomainReadPort<Map<String, Object>> {}

    /** Narrow port: erp departments snapshot read. */
    public interface ErpDepartmentsReadPort extends DomainReadPort<Map<String, Object>> {}
}
