package com.example.erp.approval.domain.delegation;

/**
 * Scope dimension of a {@link DelegationGrant} (TASK-ERP-BE-017, per-request
 * delegation scoping).
 *
 * <ul>
 *   <li>{@code GLOBAL} (default) — blanket A→D: the delegate may act for the
 *       delegator at ANY stage where A is approver (byte-identical to the
 *       pre-BE-017 behavior; {@code scopeRequestId} is null).</li>
 *   <li>{@code REQUEST} — narrowed to ONE approval request: the delegate
 *       authorizes only the request whose id equals {@code scopeRequestId}.</li>
 * </ul>
 *
 * <p>Per-route delegation (scoped to a route template) is still v2.2-deferred
 * (needs a first-class route-template identity) — not modeled here.
 */
public enum DelegationScope {
    GLOBAL,
    REQUEST
}
