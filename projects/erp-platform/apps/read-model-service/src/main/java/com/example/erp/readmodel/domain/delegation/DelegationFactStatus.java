package com.example.erp.readmodel.domain.delegation;

/**
 * The latest projected status of a delegation grant (read-only, E5;
 * TASK-ERP-BE-015). Mirrors the state observed from {@code approval-service}'s
 * {@code erp.approval.delegated.v1} (grant create) + {@code
 * erp.approval.delegation.revoked.v1} (grant revoke) events — this is NOT a local
 * state machine: the authoritative grant state + audit history live in
 * {@code approval-service}; the read-model only reflects the state it observes.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — a grant has been created ({@code delegated} event) and
 *       not yet revoked.</li>
 *   <li>{@link #REVOKED} — the grant has been revoked (sticky terminal: a late
 *       {@code delegated} arriving after {@code revoked} never reverts it).</li>
 * </ul>
 *
 * <p>Time-window <em>expiry</em> ({@code validTo} in the past) is NOT a separate
 * status — it is evaluated at read time ({@code activeAt} filter); the projected
 * status only tracks the grant/revoke lifecycle.
 */
public enum DelegationFactStatus {
    ACTIVE,
    REVOKED;

    /** {@code true} for the sticky terminal state (revoked). */
    public boolean isTerminal() {
        return this == REVOKED;
    }
}
