package com.example.erp.masterdata.application.port.outbound;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.ListDataScope;
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

    /**
     * The data scope a scoped LIST use case may read (TASK-ERP-BE-045 — {@code GET /employees/picker}). Called by
     * the application service only AFTER {@link #evaluate} returned {@code ALLOW} for the same actor, so the scope
     * decision stays inside this port (the single authorization path) and the application layer never interprets
     * {@code org_scope} itself.
     *
     * <p>Fail-CLOSED: no {@code org_scope} → {@link ListDataScope#none()} (empty list), never the whole tenant.
     */
    ListDataScope resolveListScope(ActorContext actor);
}
