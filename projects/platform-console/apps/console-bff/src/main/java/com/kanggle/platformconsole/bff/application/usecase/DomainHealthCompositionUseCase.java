package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.composition.CompositionEngine;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.port.outbound.ErpHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.FinanceHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.GapHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ScmHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.WmsHealthReadPort;
import com.kanggle.platformconsole.bff.domain.composition.DegradePolicy;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import io.micrometer.core.instrument.MeterRegistry;
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
 * <p>Fans out across all 5 backend domains in parallel via the shared
 * {@link CompositionEngine} (TASK-PC-BE-005 L6 extraction), reads each
 * domain's public Spring Boot {@code /actuator/health}, maps the result
 * to a {@link CompositionLeg}. Controller maps to § 2.4.9.2 envelope.
 *
 * <p>Hard invariants — byte-equal preserved across TASK-PC-BE-005:
 * NO outbound credential (D4 sealed-switch NEVER invoked), NO tenant
 * pass-through (actuator endpoints not tenant-scoped), all-down still
 * emits 5 legs, NO cross-leg 401 collapse (401 from any leg ⇒ that
 * card's {@code DOWNSTREAM_ERROR}), fixed leg order,
 * {@code status ∈ {ok, degraded}} only, 5s composition timeout.
 */
@Service
public class DomainHealthCompositionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DomainHealthCompositionUseCase.class);

    static final String ROUTE_LABEL = "domain-health";
    static final String DASHBOARD_LABEL = "domain-health";

    /** Fixed leg order — § 2.4.9.2 envelope schema invariant. */
    public static final List<DomainTarget> CARD_ORDER = CompositionEngine.CARD_ORDER;

    private final CompositionEngine engine;
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
        this.engine = new CompositionEngine(meterRegistry, ROUTE_LABEL);
        this.gapPort = gapPort;
        this.wmsPort = wmsPort;
        this.scmPort = scmPort;
        this.financePort = financePort;
        this.erpPort = erpPort;
    }

    /** Composes the domain-health envelope by firing 5 parallel credential-less legs. */
    public List<CompositionLeg> compose() {
        Map<DomainTarget, Supplier<CompositionLeg>> legBodies = new EnumMap<>(DomainTarget.class);
        legBodies.put(DomainTarget.GAP, () -> timed(DomainTarget.GAP, gapPort::read));
        legBodies.put(DomainTarget.WMS, () -> timed(DomainTarget.WMS, wmsPort::read));
        legBodies.put(DomainTarget.SCM, () -> timed(DomainTarget.SCM, scmPort::read));
        legBodies.put(DomainTarget.FINANCE, () -> timed(DomainTarget.FINANCE, financePort::read));
        legBodies.put(DomainTarget.ERP, () -> timed(DomainTarget.ERP, erpPort::read));

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
            LOG.warn("Domain-health composition: all 5 legs non-ok (still emitting 200 per D5.A)");
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
