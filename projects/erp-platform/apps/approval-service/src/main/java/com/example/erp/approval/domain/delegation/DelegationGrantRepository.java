package com.example.erp.approval.domain.delegation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the {@link DelegationGrant} aggregate (architecture.md
 * § v2.1 amendment). Every signature carries {@code tenantId} (defense-in-depth
 * — no cross-tenant repository method). The transition-time resolution uses
 * {@link #findActiveGrant} (fail-closed: no active grant → not authorized).
 */
public interface DelegationGrantRepository {

    /** Flush-on-save so optimistic-lock / constraint failures surface in-Tx. */
    DelegationGrant save(DelegationGrant grant);

    Optional<DelegationGrant> findById(String id, String tenantId);

    /**
     * The single ACTIVE grant {@code delegatorId → delegateId} whose validity
     * window contains {@code now} (status=ACTIVE ∧ validFrom ≤ now ∧ (validTo is
     * null ∨ validTo ≥ now)) AND whose scope covers {@code approvalRequestId}
     * (scope=GLOBAL OR (scope=REQUEST ∧ scope_request_id = approvalRequestId)).
     * Empty if none — the transition then fails closed. When both a GLOBAL and a
     * matching REQUEST grant exist, either authorizes; the query returns one
     * deterministically (GLOBAL first). The resolver re-checks {@code isActiveAt} +
     * {@code coversRequest} (defense-in-depth; TASK-ERP-BE-017).
     */
    Optional<DelegationGrant> findActiveGrant(String delegatorId, String delegateId,
                                              String tenantId, String approvalRequestId,
                                              Instant now);

    /** Grants where the principal is the delegator OR the delegate (list endpoint). */
    List<DelegationGrant> findByDelegatorOrDelegate(String principalId, String tenantId);

    /** Grants where the principal is the delegator only. */
    List<DelegationGrant> findByDelegator(String delegatorId, String tenantId);

    /** Grants where the principal is the delegate only. */
    List<DelegationGrant> findByDelegate(String delegateId, String tenantId);
}
