package com.example.erp.approval.domain.delegation;

/**
 * The outcome of resolving an acting principal against a stage approver
 * (architecture.md § v2.1 amendment — transition-time resolution).
 *
 * <ul>
 *   <li>{@code authorized = false} — the principal is neither the stage approver
 *       nor an active delegate of that approver → the use case throws
 *       {@code APPROVAL_NOT_AUTHORIZED_APPROVER} (fail-closed).</li>
 *   <li>{@code authorized = true}, {@code onBehalfOf == null} — the principal IS
 *       the stage approver (direct action).</li>
 *   <li>{@code authorized = true}, {@code onBehalfOf != null} — the principal is
 *       an active delegate acting for {@code onBehalfOf} (= the stage approver A);
 *       the audit + transition event record the delegation.</li>
 * </ul>
 */
public record DelegationResolution(boolean authorized, String onBehalfOf) {

    /** The principal is the stage approver — direct action ({@code onBehalfOf} = null). */
    public static DelegationResolution direct() {
        return new DelegationResolution(true, null);
    }

    /** The principal is an active delegate acting for {@code approverId} (A). */
    public static DelegationResolution delegated(String approverId) {
        return new DelegationResolution(true, approverId);
    }

    /** Neither the approver nor an active delegate — not authorized (fail-closed). */
    public static DelegationResolution notAuthorized() {
        return new DelegationResolution(false, null);
    }

    public boolean isDelegated() {
        return authorized && onBehalfOf != null;
    }
}
