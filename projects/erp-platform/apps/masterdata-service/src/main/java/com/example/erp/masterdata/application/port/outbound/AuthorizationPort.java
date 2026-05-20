package com.example.erp.masterdata.application.port.outbound;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;

/**
 * Single un-bypassable authorization evaluation port (erp E6, architecture.md
 * § Authorization matrix + Data scope). Every read and write use case
 * invokes {@link #evaluate} BEFORE any repository call — the decision is the
 * sole authority on access.
 *
 * <p><b>Fail-CLOSED</b>: missing role / unrecognised scope / target row's
 * owning department outside the actor's data-scope → {@code DENY_*}. There is
 * no allow-by-default codepath.
 *
 * <p>v1 adapter = {@code RoleScopeAuthorizationAdapter} (resolves roles from
 * JWT claims). v2 will swap in a {@code permission-service} client behind
 * this same port without touching the application layer.
 */
public interface AuthorizationPort {

    /**
     * Resolve an authorization decision for the given caller and target.
     *
     * @param actor caller (JWT-derived)
     * @param required coarse scope (READ / WRITE)
     * @param targetDepartmentId nullable — when null, only role check is
     *                           performed; when non-null, data-scope is also
     *                           evaluated (target row's owning department
     *                           must lie within the actor's scope subtree).
     */
    AuthorizationDecision evaluate(ActorContext actor, RequiredScope required,
                                   String targetDepartmentId);
}
