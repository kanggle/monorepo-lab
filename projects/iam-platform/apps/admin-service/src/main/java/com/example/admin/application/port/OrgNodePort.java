package com.example.admin.application.port;

import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;

import java.util.List;

/**
 * TASK-BE-492 (ADR-MONO-047 D6) — the outbound port onto account-service's org-node
 * authority. admin-service is a <b>thin command gateway</b>: it authorizes with
 * {@code org.manage}, audits, and forwards. The tree, the cycle/depth checks, and the
 * ceiling algebra live in account-service (TASK-BE-491), which owns {@code tenants} and
 * therefore {@code org_node}.
 *
 * <p><b>Failure contract.</b> Every method propagates
 * {@link com.example.admin.application.exception.DownstreamFailureException} (or
 * {@code CallNotPermittedException}) on 5xx / timeout / circuit-open, and
 * {@link com.example.admin.application.exception.OrgNodeNotFoundException} on 404. The
 * two <em>authorization-path</em> reads ({@link #subtreeTenantIds} and
 * {@link #effectiveCeiling}) are never called directly by a guard — they are wrapped by
 * fail-closed resolvers, because a failure here must NARROW reach, never widen it.
 */
public interface OrgNodePort {

    /** The full flat node list (no nesting). Callers scope it to the actor's reach. */
    List<OrgNodeView> list();

    /** @param parentId {@code null} ⇒ ROOT node */
    OrgNodeView create(String name, String parentId, CeilingView ceiling);

    OrgNodeView get(String orgNodeId);

    /**
     * Rename and/or re-parent. Both arguments are optional; {@code null} means "unchanged".
     * Cycle / depth / ceiling-subset invariants are enforced server-side.
     */
    OrgNodeView update(String orgNodeId, String name, String parentId);

    /** Deletes a node with no children and no tenants (server-enforced). */
    void delete(String orgNodeId);

    /** Replaces the node's own ceiling wholesale (subset invariants server-enforced). */
    OrgNodeView setCeiling(String orgNodeId, CeilingView ceiling);

    /**
     * The tenants of the node and every descendant.
     *
     * <p><b>Authorization path.</b> Consumed by {@code AdminGrantScopeEvaluator} to expand a
     * node-scoped grant. A failure MUST resolve to the EMPTY set — never {@code '*'}, never
     * all tenants, and a failure must never be cached as a permissive value. See
     * {@code OrgNodeSubtreeResolver}.
     */
    List<String> subtreeTenantIds(String orgNodeId);

    /**
     * The intersection of every ceiling on the {@code root → node} chain.
     *
     * <p><b>Authorization path.</b> Backs the {@code ORG_ADMIN} grant cap. A failure MUST
     * resolve to {@link CeilingView#failClosed()} ({@code BOUNDED([])}, permitting nothing)
     * — never to {@code UNBOUNDED}.
     */
    CeilingView effectiveCeiling(String orgNodeId);
}
