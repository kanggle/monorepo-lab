package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.composition.CompositionEngine;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.port.outbound.EcommerceHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ErpHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.FinanceHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.IamHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ScmHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.WmsHealthReadPort;
import com.kanggle.platformconsole.bff.domain.composition.DegradePolicy;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Phase 7 "Domain Health Overview" composition use-case.
 *
 * <p>Fans out across all 6 backend domains in parallel via the shared
 * {@link CompositionEngine} (TASK-PC-BE-005 L6 extraction; ecommerce 6th leg
 * added by TASK-MONO-241), reads each domain's public Spring Boot
 * {@code /actuator/health}, maps the result to a {@link CompositionLeg}.
 * Controller maps to § 2.4.9.2 envelope.
 *
 * <p><b>Card order (TASK-MONO-241)</b>: this use-case owns its own
 * {@link #CARD_ORDER} (6 domains) — it no longer aliases
 * {@code CompositionEngine.CARD_ORDER} (5). This is what lets the Domain
 * Health surface be 6 cards while the Operator Overview (§ 2.4.9.1) stays 5,
 * over the same order-agnostic {@link CompositionEngine}.
 *
 * <p>Hard invariants — byte-equal preserved across TASK-PC-BE-005:
 * NO outbound credential (D4 sealed-switch NEVER invoked), NO tenant
 * pass-through (actuator endpoints not tenant-scoped), all-down still
 * emits 6 legs, NO cross-leg 401 collapse (401 from any leg ⇒ that
 * card's {@code DOWNSTREAM_ERROR}), fixed leg order,
 * {@code status ∈ {ok, degraded}} only, 5s composition timeout.
 */
@Service
public class DomainHealthCompositionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DomainHealthCompositionUseCase.class);

    static final String ROUTE_LABEL = "domain-health";
    static final String DASHBOARD_LABEL = "domain-health";

    /**
     * Fixed leg order — § 2.4.9.2 envelope schema invariant. Owned by this
     * use-case (TASK-MONO-241) so the Domain Health surface is independently
     * 6 cards, not coupled to the 5-leg Operator Overview via the shared engine.
     * {@code ECOMMERCE} is appended last → existing 5 cards keep their order.
     */
    public static final List<DomainTarget> CARD_ORDER = List.of(
            DomainTarget.IAM,
            DomainTarget.WMS,
            DomainTarget.SCM,
            DomainTarget.FINANCE,
            DomainTarget.ERP,
            DomainTarget.ECOMMERCE
    );

    private final CompositionEngine engine;
    private final IamHealthReadPort gapPort;
    private final WmsHealthReadPort wmsPort;
    private final ScmHealthReadPort scmPort;
    private final FinanceHealthReadPort financePort;
    private final ErpHealthReadPort erpPort;
    private final EcommerceHealthReadPort ecommercePort;

    public DomainHealthCompositionUseCase(
            MeterRegistry meterRegistry,
            Tracer tracer,
            IamHealthReadPort gapPort,
            WmsHealthReadPort wmsPort,
            ScmHealthReadPort scmPort,
            FinanceHealthReadPort financePort,
            ErpHealthReadPort erpPort,
            EcommerceHealthReadPort ecommercePort) {
        this.engine = new CompositionEngine(meterRegistry, tracer, ROUTE_LABEL);
        this.gapPort = gapPort;
        this.wmsPort = wmsPort;
        this.scmPort = scmPort;
        this.financePort = financePort;
        this.erpPort = erpPort;
        this.ecommercePort = ecommercePort;
    }

    /** Composes the domain-health envelope by firing 6 parallel credential-less legs. */
    public List<CompositionLeg> compose() {
        Map<DomainTarget, Supplier<CompositionLeg>> legBodies = new EnumMap<>(DomainTarget.class);
        legBodies.put(DomainTarget.IAM, () -> timed(DomainTarget.IAM, gapPort::read));
        legBodies.put(DomainTarget.WMS, () -> timed(DomainTarget.WMS, wmsPort::read));
        legBodies.put(DomainTarget.SCM, () -> timed(DomainTarget.SCM, scmPort::read));
        legBodies.put(DomainTarget.FINANCE, () -> timed(DomainTarget.FINANCE, financePort::read));
        legBodies.put(DomainTarget.ERP, () -> timed(DomainTarget.ERP, erpPort::read));
        legBodies.put(DomainTarget.ECOMMERCE, () -> timed(DomainTarget.ECOMMERCE, ecommercePort::read));

        Map<DomainTarget, CompositionLeg> results = engine.fanOut(null, legBodies);

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
            LOG.warn("Domain-health composition: all 6 legs non-ok (still emitting 200 per D5.A)");
        }
        return ordered;
    }

    /** Wraps a parameterless health-read into the engine timer + classifier. */
    private CompositionLeg timed(DomainTarget domain, Supplier<Map<String, Object>> read) {
        return engine.time(domain,
                () -> CompositionLeg.ok(LegOutcome.ok(domain), read.get()),
                this::classifyError);
    }

    /**
     * Domain Health leg error classifier — byte-equal with the historic
     * in-line {@code time(...)} catch chain. Collapses every
     * {@link HttpClientErrorException} (incl. 401/403) to
     * {@code degraded / DOWNSTREAM_ERROR} (no permission outcome on a
     * public actuator leg), treats socket timeout as {@code TIMEOUT}.
     */
    private CompositionLeg classifyError(DomainTarget domain, Throwable e) {
        if (e instanceof HttpClientErrorException) {
            engine.emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        }
        if (e instanceof ResourceAccessException rae) {
            if (rae.getCause() instanceof SocketTimeoutException) {
                engine.emitErrorCounter(domain, "timeout");
                return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "TIMEOUT"));
            }
            engine.emitErrorCounter(domain, "5xx");
            return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
        }
        engine.emitErrorCounter(domain, "5xx");
        LOG.warn("Leg {} unexpected error: {}: {}", domain,
                e.getClass().getSimpleName(), e.getMessage());
        return CompositionLeg.outcomeOnly(LegOutcome.degraded(domain, "DOWNSTREAM_ERROR"));
    }
}
