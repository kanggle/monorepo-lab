package com.example.erp.approval.application.port.outbound;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.domain.authorization.AuthorizationDecision;
import com.example.erp.approval.domain.authorization.RequiredScope;

/**
 * Single un-bypassable authorization evaluation port (erp E6, architecture.md
 * § Approver authorization + Data scope). Every read and transition use case
 * invokes {@link #evaluate} BEFORE any repository call — the decision is the
 * sole authority on role + data-scope access. The approval-specific
 * approver-eligibility check (request's approver == acting principal) is a
 * SEPARATE domain guard on the aggregate (Separation of Duties — I4).
 *
 * <p><b>Fail-CLOSED</b>: missing role / unrecognised scope / target outside the
 * actor's data-scope → {@code DENY_*}. No allow-by-default codepath.
 */
public interface AuthorizationPort {

    /**
     * @param actor caller (JWT-derived)
     * @param required coarse scope (READ / WRITE)
     * @param targetDepartmentId nullable — when null, only the role check runs;
     *                           when non-null, data-scope is also evaluated.
     */
    AuthorizationDecision evaluate(ActorContext actor, RequiredScope required,
                                   String targetDepartmentId);
}
