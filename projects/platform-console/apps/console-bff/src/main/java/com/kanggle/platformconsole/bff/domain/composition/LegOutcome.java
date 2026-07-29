package com.kanggle.platformconsole.bff.domain.composition;

import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;

/**
 * The outcome of a single outbound domain leg in a composition fan-out.
 *
 * <p>Three possible states per § 2.4.9 resilience discipline (ADR-MONO-017 D5.A):
 * <ul>
 *   <li>{@link Status#OK} — the leg responded successfully.</li>
 *   <li>{@link Status#DEGRADED} — the leg failed (5xx / timeout / circuit_open);
 *       the composition renders a degraded placeholder card for this domain.</li>
 *   <li>{@link Status#FORBIDDEN} — the leg returned 403 (tenant or permission scope
 *       denied); rendered as a per-card "scope denied" placeholder — NOT the same as
 *       degraded (the operator console must distinguish the two).</li>
 * </ul>
 *
 * <p><b>{@code circuit_open} is a {@code reason}, not a fourth {@link Status}</b>
 * (TASK-PC-BE-015). The contract's card schema fixes
 * {@code status ∈ {ok, degraded, forbidden}} (§ 2.4.9.1) and
 * {@code status ∈ {ok, degraded}} (§ 2.4.9.2), while the degraded {@code reason}
 * union is {@code { DOWNSTREAM_ERROR, TIMEOUT, CIRCUIT_OPEN }} in both — which is
 * exactly what {@code console-web}'s {@code DEGRADED_REASONS} zod enums already
 * accept. Adding a status member would have been a wire-breaking change to render
 * a value the FE was already prepared for; {@link #circuitOpen(DomainTarget)}
 * supplies the missing emitter without touching the envelope shape.
 *
 * <p>All-down composition still returns 200 with an all-degraded envelope
 * (ADR-MONO-017 D5.B rejection: BFF-level all-or-nothing timeout MUST NOT appear).
 */
public record LegOutcome(DomainTarget domain, Status status, String reason) {

    /**
     * Degrade reason emitted when the leg's {@code (domain, route)} circuit
     * breaker is OPEN and the call was rejected without any outbound request.
     * Wire-visible on the composed card; matches the contract's degraded-reason
     * union verbatim.
     */
    public static final String REASON_CIRCUIT_OPEN = "CIRCUIT_OPEN";

    public enum Status {
        OK, DEGRADED, FORBIDDEN
    }

    public static LegOutcome ok(DomainTarget domain) {
        return new LegOutcome(domain, Status.OK, null);
    }

    public static LegOutcome degraded(DomainTarget domain, String reason) {
        return new LegOutcome(domain, Status.DEGRADED, reason);
    }

    /**
     * Constructs the fail-fast degrade emitted when the per-leg circuit breaker
     * is OPEN (TASK-PC-BE-015). Distinct from
     * {@code degraded(domain, "DOWNSTREAM_ERROR")}: the leg never reached the
     * producer, so the operator is told "temporarily unreachable, backing off",
     * not "the producer answered with an error".
     */
    public static LegOutcome circuitOpen(DomainTarget domain) {
        return new LegOutcome(domain, Status.DEGRADED, REASON_CIRCUIT_OPEN);
    }

    public static LegOutcome forbidden(DomainTarget domain, String reason) {
        return new LegOutcome(domain, Status.FORBIDDEN, reason);
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public boolean isDegraded() {
        return status == Status.DEGRADED;
    }

    public boolean isForbidden() {
        return status == Status.FORBIDDEN;
    }

    /**
     * True iff this outcome is the fail-fast circuit-open degrade. A convenience
     * predicate for tests and log lines — the wire shape is unchanged
     * ({@code status=degraded}).
     */
    public boolean isCircuitOpen() {
        return status == Status.DEGRADED && REASON_CIRCUIT_OPEN.equals(reason);
    }
}
