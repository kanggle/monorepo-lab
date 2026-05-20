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
 * <p>All-down composition still returns 200 with an all-degraded envelope
 * (ADR-MONO-017 D5.B rejection: BFF-level all-or-nothing timeout MUST NOT appear).
 */
public record LegOutcome(DomainTarget domain, Status status, String reason) {

    public enum Status {
        OK, DEGRADED, FORBIDDEN
    }

    public static LegOutcome ok(DomainTarget domain) {
        return new LegOutcome(domain, Status.OK, null);
    }

    public static LegOutcome degraded(DomainTarget domain, String reason) {
        return new LegOutcome(domain, Status.DEGRADED, reason);
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
}
