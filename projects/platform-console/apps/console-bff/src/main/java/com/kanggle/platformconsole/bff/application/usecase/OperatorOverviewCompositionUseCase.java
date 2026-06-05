package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.composition.CompositionEngine;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.port.outbound.ErpDepartmentsReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.FinanceBalanceReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.IamAccountsReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ScmInventoryReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.WmsInventoryReadPort;
import com.kanggle.platformconsole.bff.domain.composition.DegradePolicy;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MVP "Operator Overview" composition use-case.
 *
 * <p>Fans out across all 5 backend domains in parallel via the shared
 * {@link CompositionEngine} (TASK-PC-BE-005 L6 extraction). Hard
 * invariants (byte-equal across the extraction): D4 per-domain credential
 * dispatch (sealed-switch on {@link OutboundCredential}), cross-leg 401
 * ⇒ {@link UpstreamUnauthorizedException}, fixed leg order, tenant
 * pass-through (D6.A), 5s composition timeout, all-down still emits 5
 * legs (controller emits HTTP 200), finance Option (a) activation
 * (header-non-blank ⇒ {@code readBalances}; blank ⇒
 * {@code forbidden / MISSING_PREREQUISITE} with no outbound HTTP call).
 */
@Service
public class OperatorOverviewCompositionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorOverviewCompositionUseCase.class);

    static final String ROUTE_LABEL = "operator-overview";
    static final String DASHBOARD_LABEL = "operator-overview";

    /** Fixed leg order — § 2.4.9.1 envelope schema invariant. */
    public static final List<DomainTarget> CARD_ORDER = CompositionEngine.CARD_ORDER;

    private final CredentialSelectionPort credentialSelection;
    private final CompositionEngine engine;
    private final IamAccountsReadPort gapPort;
    private final WmsInventoryReadPort wmsPort;
    private final ScmInventoryReadPort scmPort;
    private final FinanceBalanceReadPort financePort;
    private final ErpDepartmentsReadPort erpPort;

    public OperatorOverviewCompositionUseCase(
            CredentialSelectionPort credentialSelection,
            MeterRegistry meterRegistry,
            Tracer tracer,
            IamAccountsReadPort gapPort,
            WmsInventoryReadPort wmsPort,
            ScmInventoryReadPort scmPort,
            FinanceBalanceReadPort financePort,
            ErpDepartmentsReadPort erpPort) {
        this.credentialSelection = credentialSelection;
        this.engine = new CompositionEngine(meterRegistry, tracer, ROUTE_LABEL);
        this.gapPort = gapPort;
        this.wmsPort = wmsPort;
        this.scmPort = scmPort;
        this.financePort = financePort;
        this.erpPort = erpPort;
    }

    /** Backward-compatible 1-arg overload — equivalent to {@code compose(tenantId, null)}. */
    public List<CompositionLeg> compose(String tenantId) {
        return compose(tenantId, null);
    }

    /**
     * Composes the operator overview by firing 5 parallel outbound legs.
     *
     * @param tenantId                 active tenant forwarded verbatim on every leg (D6.A)
     * @param financeDefaultAccountId  optional operator finance default account id
     *                                 (§ 2.4.9.1 Option (a) activation; null/blank
     *                                 ⇒ finance leg renders
     *                                 {@code forbidden / MISSING_PREREQUISITE}
     *                                 without any outbound HTTP call)
     */
    public List<CompositionLeg> compose(String tenantId, String financeDefaultAccountId) {
        // Pre-resolve credentials on the SERVLET THREAD (virtual threads
        // spawned by the engine inherit no request scope).
        Map<DomainTarget, OutboundCredential> preResolved = new EnumMap<>(DomainTarget.class);
        Map<DomainTarget, CompositionLeg> earlyDecided = new EnumMap<>(DomainTarget.class);
        for (DomainTarget domain : CARD_ORDER) {
            try {
                preResolved.put(domain, credentialSelection.selectFor(domain));
            } catch (MissingCredentialException mce) {
                engine.emitErrorCounter(domain, "missing_prerequisite");
                earlyDecided.put(domain,
                        CompositionLeg.outcomeOnly(LegOutcome.forbidden(domain, "MISSING_PREREQUISITE")));
            }
        }

        Map<DomainTarget, Supplier<CompositionLeg>> legBodies = new EnumMap<>(DomainTarget.class);
        legBodies.put(DomainTarget.GAP, legBody(DomainTarget.GAP, earlyDecided, preResolved,
                cred -> callRead(DomainTarget.GAP, tenantId, cred, gapPort::read)));
        legBodies.put(DomainTarget.WMS, legBody(DomainTarget.WMS, earlyDecided, preResolved,
                cred -> callRead(DomainTarget.WMS, tenantId, cred, wmsPort::read)));
        legBodies.put(DomainTarget.SCM, legBody(DomainTarget.SCM, earlyDecided, preResolved,
                cred -> callRead(DomainTarget.SCM, tenantId, cred, scmPort::read)));
        legBodies.put(DomainTarget.FINANCE, legBody(DomainTarget.FINANCE, earlyDecided, preResolved,
                cred -> callFinance(tenantId, cred, financeDefaultAccountId)));
        legBodies.put(DomainTarget.ERP, legBody(DomainTarget.ERP, earlyDecided, preResolved,
                cred -> callRead(DomainTarget.ERP, tenantId, cred, erpPort::read)));

        Map<DomainTarget, CompositionLeg> results = engine.fanOut(tenantId, legBodies);

        // Cross-leg 401 collapse (§ 2.4.4 D3 / § 2.4.9.1).
        for (CompositionLeg leg : results.values()) {
            if (leg.unauthorized()) {
                throw new UpstreamUnauthorizedException(
                        "Upstream leg returned 401 — composition collapses to 401 (cross-leg discipline)");
            }
        }

        // Per-leg degrade-counter emission + fixed-order assembly.
        List<LegOutcome> outcomesForPolicy = new ArrayList<>();
        List<CompositionLeg> ordered = new ArrayList<>(CARD_ORDER.size());
        for (DomainTarget domain : CARD_ORDER) {
            CompositionLeg leg = results.get(domain);
            outcomesForPolicy.add(leg.outcome());
            ordered.add(leg);
            if (!leg.outcome().isOk()) {
                engine.emitAggregationDegradeCounter(DASHBOARD_LABEL, domain);
            }
        }
        if (DegradePolicy.isAllDown(outcomesForPolicy)) {
            LOG.warn("Operator-overview composition: all 5 legs non-ok (still emitting 200 per D5.A)");
        }
        return ordered;
    }

    /**
     * Builds a per-leg body supplier — returns the {@code earlyDecided}
     * outcome directly (no timing) or dispatches via the engine's per-leg
     * timer + classifier wrapper.
     */
    private Supplier<CompositionLeg> legBody(
            DomainTarget domain,
            Map<DomainTarget, CompositionLeg> earlyDecided,
            Map<DomainTarget, OutboundCredential> preResolved,
            Function<OutboundCredential, CompositionLeg> body) {
        return () -> {
            CompositionLeg early = earlyDecided.get(domain);
            if (early != null) {
                return early;
            }
            return engine.time(domain, () -> body.apply(preResolved.get(domain)), this::classifyError);
        };
    }

    /** Generic per-leg read: bearer-resolve (D4 sealed switch) + port invoke + ok-wrap. */
    private CompositionLeg callRead(DomainTarget domain, String tenantId, OutboundCredential cred,
                                     BiFunction<String, String, Map<String, Object>> read) {
        return CompositionLeg.ok(LegOutcome.ok(domain), read.apply(tenantId, bearerFromCred(cred)));
    }

    /**
     * Finance leg — Option (a) activation: header-non-blank ⇒
     * {@code readBalances}; blank/null ⇒ short-circuit to
     * {@code MISSING_PREREQUISITE} with NO outbound HTTP call.
     */
    private CompositionLeg callFinance(String tenantId, OutboundCredential cred, String accountId) {
        if (!StringUtils.hasText(accountId)) {
            engine.emitErrorCounter(DomainTarget.FINANCE, "missing_prerequisite");
            return CompositionLeg.outcomeOnly(
                    LegOutcome.forbidden(DomainTarget.FINANCE, "MISSING_PREREQUISITE"));
        }
        return CompositionLeg.ok(LegOutcome.ok(DomainTarget.FINANCE),
                financePort.readBalances(tenantId, bearerFromCred(cred), accountId.trim()));
    }

    /** Sealed-switch on the resolved {@link OutboundCredential} (D4 consumer side). */
    private static String bearerFromCred(OutboundCredential cred) {
        return switch (cred) {
            case OutboundCredential.OperatorToken t -> t.token();
            case OutboundCredential.GapOidcAccessToken t -> t.token();
        };
    }

    /**
     * Operator Overview leg error classifier — byte-equal with the historic
     * in-line {@code time(...)} catch chain.
     */
    private CompositionLeg classifyError(DomainTarget domain, Throwable e) {
        if (e instanceof MissingCredentialException) {
            engine.emitErrorCounter(domain, "missing_prerequisite");
            return CompositionLeg.outcomeOnly(LegOutcome.forbidden(domain, "MISSING_PREREQUISITE"));
        }
        if (e instanceof HttpClientErrorException.Unauthorized) {
            engine.emitErrorCounter(domain, "5xx");
            return CompositionLeg.unauthorized(domain);
        }
        if (e instanceof HttpClientErrorException.Forbidden fe) {
            String reason = classifyForbidden(fe);
            engine.emitErrorCounter(domain,
                    "TENANT_FORBIDDEN".equals(reason) ? "tenant_forbidden" : "permission_denied");
            return CompositionLeg.outcomeOnly(LegOutcome.forbidden(domain, reason));
        }
        if (e instanceof HttpClientErrorException) {
            engine.emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        }
        if (e instanceof ResourceAccessException rae
                && rae.getCause() instanceof SocketTimeoutException) {
            engine.emitErrorCounter(domain, "timeout");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "TIMEOUT"));
        }
        engine.emitErrorCounter(domain, "5xx");
        if (!(e instanceof ResourceAccessException)) {
            LOG.warn("Leg {} unexpected error: {}: {}", domain,
                    e.getClass().getSimpleName(), e.getMessage());
        }
        return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
    }

    /**
     * 403 may signal {@code TENANT_FORBIDDEN} or generic
     * {@code PERMISSION_DENIED}. Peek at the producer envelope body.
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
}
