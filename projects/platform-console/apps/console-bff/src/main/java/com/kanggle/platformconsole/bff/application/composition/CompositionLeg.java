package com.kanggle.platformconsole.bff.application.composition;

import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;

/**
 * Composition leg result — pairs the {@link LegOutcome} (status + reason)
 * with the optional ok-payload (domain-shaped, typed as {@code Object} so
 * either domain map shapes or actuator health JSON can flow through). The
 * controller maps to the wire envelope.
 *
 * <p>Extracted from the historic nested
 * {@code OperatorOverviewCompositionUseCase.CompositionLeg} +
 * {@code DomainHealthCompositionUseCase.CompositionLeg} records
 * (TASK-PC-BE-005 L4 Move Class + duplication consolidation). The two
 * historic records were near-identical (3-field vs 2-field with the
 * Health variant lacking the {@code unauthorized} flag) — unified here.
 * The Domain Health route never sets {@code unauthorized=true} (its
 * use-case treats 401 from a public actuator leg as
 * {@code DOWNSTREAM_ERROR}, not a cross-leg collapse signal), so the
 * unified shape is byte-equal observable on its wire envelope.
 *
 * @param outcome       the leg's classified outcome
 * @param data          the ok-payload (null on degraded / forbidden)
 * @param unauthorized  true iff the upstream leg returned 401 (only set
 *                      by {@link #unauthorized(DomainTarget)}); Operator
 *                      Overview collapses cross-leg 401 into a
 *                      composition-level 401, Domain Health does not
 *                      surface this flag at all
 */
public record CompositionLeg(LegOutcome outcome, Object data, boolean unauthorized) {

    /**
     * Constructs an ok-payload leg.
     */
    public static CompositionLeg ok(LegOutcome outcome, Object data) {
        return new CompositionLeg(outcome, data, false);
    }

    /**
     * Constructs an outcome-only leg (degraded / forbidden, no data
     * payload).
     */
    public static CompositionLeg outcomeOnly(LegOutcome outcome) {
        return new CompositionLeg(outcome, null, false);
    }

    /**
     * Constructs an upstream-401 leg — emitted only by the Operator Overview
     * composition use-case, which translates it into a cross-leg 401 collapse
     * (the inbound handler maps {@code UpstreamUnauthorizedException} to
     * {@code 401 TOKEN_INVALID}).
     */
    public static CompositionLeg unauthorized(DomainTarget domain) {
        return new CompositionLeg(LegOutcome.degraded(domain, "UNAUTHORIZED"), null, true);
    }
}
