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
import org.springframework.util.StringUtils;
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
 *   <li><b>Finance leg — Option (a) activation (TASK-PC-FE-014, both paths
 *       first-class)</b> — when the caller supplies a non-blank
 *       {@code financeDefaultAccountId} (sourced from the GAP registry
 *       {@code productItem.operatorContext.defaultAccountId} per Phase 1
 *       TASK-BE-304 and forwarded by console-web as the
 *       {@code X-Finance-Default-Account-Id} request header per § 2.4.9.1
 *       Implementation guidance Option (a) activation), the leg fires
 *       {@code FinanceBalanceReadPort.readBalances(...)} and renders the
 *       balances payload. When the id is absent/blank (operator profile has
 *       no {@code admin_operators.finance_default_account_id}), the leg
 *       short-circuits to {@code forbidden / MISSING_PREREQUISITE} and the
 *       outbound HTTP call is NEVER fired. Both branches are first-class
 *       honest UX (per task md § Decision authority).</li>
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
    private final FinanceBalanceReadPort financePort;
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
     * Backward-compatible 1-arg overload — equivalent to
     * {@code compose(tenantId, null)} (finance leg follows the
     * {@code MISSING_PREREQUISITE} branch). Retained as a thin pass-through
     * for any in-process direct callers; the production inbound path
     * ({@code OperatorOverviewController}) uses the 2-arg overload to thread
     * the optional {@code X-Finance-Default-Account-Id} request header
     * (TASK-PC-FE-014).
     */
    public List<CompositionLeg> compose(String tenantId) {
        return compose(tenantId, null);
    }

    /**
     * Composes the operator overview by firing 5 parallel outbound legs.
     *
     * @param tenantId                 active tenant forwarded verbatim on every leg (D6.A)
     * @param financeDefaultAccountId  optional operator finance default account id
     *                                 (sourced from the GAP registry
     *                                 {@code productItem.operatorContext.defaultAccountId}
     *                                 and forwarded as the
     *                                 {@code X-Finance-Default-Account-Id} request header
     *                                 per § 2.4.9.1 Option (a) activation; nullable/blank
     *                                 → finance leg renders
     *                                 {@code forbidden / MISSING_PREREQUISITE}
     *                                 without any outbound HTTP call)
     * @return the fixed-order 5-leg composition result (gap, wms, scm,
     *         finance, erp); each leg carries its {@link LegOutcome} and the
     *         optional ok-payload
     * @throws UpstreamUnauthorizedException if any outbound leg returned 401
     * @throws MissingCredentialException    if a required inbound token is absent
     *                                       at top-level (defensive — most
     *                                       absence cases are turned into
     *                                       per-leg outcomes)
     */
    public List<CompositionLeg> compose(String tenantId, String financeDefaultAccountId) {
        // (0) Pre-resolve credentials on the SERVLET THREAD where the request
        //     scope is active. Each virtual thread spawned by fanOut() runs in
        //     its OWN thread context — Spring's RequestContextHolder is thread-
        //     local (inheritable=false), so dereferencing the @RequestScope
        //     `OperatorCredentialContext` proxy inside a virtual thread throws
        //     ScopeNotActiveException. Resolving here once, then passing plain
        //     `OutboundCredential` records into the fan-out, keeps virtual
        //     threads free of any request-scoped state.
        //
        //     ADR-MONO-017 D4 HARD INVARIANT is fully preserved: the sealed
        //     switch in CredentialSelectionAdapter is invoked exactly as before
        //     (5 calls, no fallback, no default arm). Per-leg failure surfaces
        //     are unchanged — MissingCredentialException at resolve time maps
        //     to the same `forbidden / MISSING_PREREQUISITE` per-card outcome
        //     that the previous in-virtual-thread time() handler emitted.
        Map<DomainTarget, OutboundCredential> preResolved = new EnumMap<>(DomainTarget.class);
        Map<DomainTarget, CompositionLeg> earlyDecided = new EnumMap<>(DomainTarget.class);
        for (DomainTarget domain : CARD_ORDER) {
            try {
                preResolved.put(domain, credentialSelection.selectFor(domain));
            } catch (MissingCredentialException mce) {
                emitErrorCounter(domain, "missing_prerequisite");
                earlyDecided.put(domain,
                        CompositionLeg.outcomeOnly(LegOutcome.forbidden(domain, "MISSING_PREREQUISITE")));
            }
        }

        Map<DomainTarget, CompositionLeg> results = fanOut(
                tenantId, preResolved, earlyDecided, financeDefaultAccountId);

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

    private Map<DomainTarget, CompositionLeg> fanOut(
            String tenantId,
            Map<DomainTarget, OutboundCredential> preResolved,
            Map<DomainTarget, CompositionLeg> earlyDecided,
            String financeDefaultAccountId) {
        // Java 21 virtual-thread executor: each leg gets its own VT.
        // Each leg receives its credential as a PLAIN VALUE (record) — no
        // request-scoped bean is dereferenced inside the virtual thread.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<CompositionLeg> gapFuture = legFuture(executor,
                    DomainTarget.GAP, earlyDecided,
                    cred -> callGap(tenantId, cred), preResolved);
            CompletableFuture<CompositionLeg> wmsFuture = legFuture(executor,
                    DomainTarget.WMS, earlyDecided,
                    cred -> callWms(tenantId, cred), preResolved);
            CompletableFuture<CompositionLeg> scmFuture = legFuture(executor,
                    DomainTarget.SCM, earlyDecided,
                    cred -> callScm(tenantId, cred), preResolved);
            CompletableFuture<CompositionLeg> financeFuture = legFuture(executor,
                    DomainTarget.FINANCE, earlyDecided,
                    cred -> callFinance(tenantId, cred, financeDefaultAccountId), preResolved);
            CompletableFuture<CompositionLeg> erpFuture = legFuture(executor,
                    DomainTarget.ERP, earlyDecided,
                    cred -> callErp(tenantId, cred), preResolved);

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

    /**
     * Builds the future for a single leg. Honours an {@code earlyDecided}
     * outcome (e.g. {@code MISSING_PREREQUISITE} from credential pre-resolve)
     * without spawning a virtual thread; otherwise dispatches the body with
     * the pre-resolved {@link OutboundCredential}.
     */
    private static CompletableFuture<CompositionLeg> legFuture(
            ExecutorService executor,
            DomainTarget domain,
            Map<DomainTarget, CompositionLeg> earlyDecided,
            java.util.function.Function<OutboundCredential, CompositionLeg> body,
            Map<DomainTarget, OutboundCredential> preResolved) {
        CompositionLeg early = earlyDecided.get(domain);
        if (early != null) {
            return CompletableFuture.completedFuture(early);
        }
        OutboundCredential cred = preResolved.get(domain);
        return CompletableFuture.supplyAsync(() -> body.apply(cred), executor);
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

    private CompositionLeg callGap(String tenantId, OutboundCredential cred) {
        return time(DomainTarget.GAP, () -> {
            String bearer = bearerFromCred(cred);
            Map<String, Object> data = gapPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.GAP), data);
        });
    }

    private CompositionLeg callWms(String tenantId, OutboundCredential cred) {
        return time(DomainTarget.WMS, () -> {
            String bearer = bearerFromCred(cred);
            Map<String, Object> data = wmsPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.WMS), data);
        });
    }

    private CompositionLeg callScm(String tenantId, OutboundCredential cred) {
        return time(DomainTarget.SCM, () -> {
            String bearer = bearerFromCred(cred);
            Map<String, Object> data = scmPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), data);
        });
    }

    /**
     * Finance leg — Option (a) activation (TASK-PC-FE-014, both paths first-class
     * per § 2.4.9.1 Implementation guidance):
     *
     * <ul>
     *   <li><b>Header-present (non-blank {@code accountId}):</b> routes through
     *       {@link FinanceBalanceReadPort#readBalances(String, String, String)} —
     *       outbound {@code GET /api/finance/accounts/{accountId}/balances} with the
     *       GAP OIDC access token as bearer (per § 2.4.9.1 row 4 / § 2.4.7). The leg
     *       renders the balances payload on the {@code ok} card.</li>
     *   <li><b>Header-absent/blank:</b> surfaces
     *       {@code forbidden / MISSING_PREREQUISITE} <b>without</b> firing any
     *       outbound HTTP call. This is the honest UX for operators whose
     *       {@code admin_operators.finance_default_account_id} column is
     *       {@code NULL} (the default post-V0028 migration).</li>
     * </ul>
     *
     * <p>Both branches are first-class — neither is a transient or deprecated
     * state. The unit / slice / IT tests cover both.
     *
     * @param tenantId  active tenant forwarded verbatim (D6.A)
     * @param cred      pre-resolved finance credential (GAP OIDC access token
     *                  per § 2.4.9.1 row 4)
     * @param accountId the operator's {@code finance_default_account_id} as
     *                  forwarded by console-web; null/blank/whitespace-only ⇒
     *                  short-circuit to {@code MISSING_PREREQUISITE}
     */
    private CompositionLeg callFinance(String tenantId, OutboundCredential cred, String accountId) {
        if (!StringUtils.hasText(accountId)) {
            emitErrorCounter(DomainTarget.FINANCE, "missing_prerequisite");
            return CompositionLeg.outcomeOnly(
                    LegOutcome.forbidden(DomainTarget.FINANCE, "MISSING_PREREQUISITE"));
        }
        return time(DomainTarget.FINANCE, () -> {
            String bearer = bearerFromCred(cred);
            Map<String, Object> data = financePort.readBalances(tenantId, bearer, accountId.trim());
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.FINANCE), data);
        });
    }

    private CompositionLeg callErp(String tenantId, OutboundCredential cred) {
        return time(DomainTarget.ERP, () -> {
            String bearer = bearerFromCred(cred);
            Map<String, Object> data = erpPort.read(tenantId, bearer);
            return CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), data);
        });
    }

    /**
     * Returns the bearer-token string from a pre-resolved
     * {@link OutboundCredential}. Switches on the sealed hierarchy — no
     * default arm, no fallback (ADR-MONO-017 D4 HARD INVARIANT). The
     * sealed-switch lives in {@code CredentialSelectionAdapter}; this helper
     * is its dual on the consumer side (reading the resolved record).
     */
    private static String bearerFromCred(OutboundCredential cred) {
        return switch (cred) {
            case OutboundCredential.OperatorToken t -> t.token();
            case OutboundCredential.GapOidcAccessToken t -> t.token();
        };
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

    /**
     * Narrow port: finance balance read (operator default account id resolved upstream).
     *
     * <p>The {@link #read(String, String)} inherited from
     * {@link DomainReadPort} remains for contract conformance but is the
     * <b>marker for the inactive path</b> — the finance leg never uses it
     * (the adapter throws {@link UnsupportedOperationException}). The active
     * path is {@link #readBalances(String, String, String)}, which the
     * use-case invokes once Option (a) is satisfied (TASK-PC-FE-014).
     */
    public interface FinanceBalanceReadPort extends DomainReadPort<Map<String, Object>> {

        /**
         * TASK-PC-FE-014 Option (a) activation: reads the operator's default
         * finance account balances. The caller (use-case) guarantees
         * {@code accountId} is non-blank — the use-case applies
         * {@link StringUtils#hasText(CharSequence)} before invoking; blank or
         * null short-circuits to {@code MISSING_PREREQUISITE} on the use-case
         * side and this method is never reached. The {@link #read(String, String)}
         * default remains the {@link DomainReadPort} contract conformance
         * point but is {@code UnsupportedOperationException}-throwing on the
         * concrete adapter — the marker that the active path is
         * {@code readBalances}.
         *
         * @param tenantId   active tenant forwarded verbatim (D6.A)
         * @param credential GAP OIDC access token bearer value
         *                   (per § 2.4.9.1 row 4 / § 2.4.7)
         * @param accountId  operator's finance default account id (non-blank;
         *                   sourced from GAP registry
         *                   {@code productItem.operatorContext.defaultAccountId}
         *                   per TASK-BE-304, threaded through the
         *                   {@code X-Finance-Default-Account-Id} request header
         *                   per § 2.4.9.1 Option (a) activation)
         * @return the balances payload (domain-shaped {@code Map}); never
         *         {@code null}
         */
        Map<String, Object> readBalances(String tenantId, String credential, String accountId);
    }

    /** Narrow port: erp departments snapshot read. */
    public interface ErpDepartmentsReadPort extends DomainReadPort<Map<String, Object>> {}
}
