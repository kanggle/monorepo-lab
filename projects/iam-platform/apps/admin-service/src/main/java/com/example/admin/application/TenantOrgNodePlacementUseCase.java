package com.example.admin.application;

import com.example.admin.application.orgnode.TenantPlacementView;
import com.example.admin.application.port.OrgNodePort;
import com.example.admin.application.port.TenantPlacementPort;
import com.example.admin.application.tenant.CreateTenantUseCase;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07) — putting a tenant under an org-node, moving it,
 * or taking it out. One write; the owner's decision is «양쪽을 다 관리하는 사람만»: the actor must
 * administer both where the tenant is now and where it is going
 * ({@link OrgNodeScopeGuard#requirePlacementAllowed} — a composition of the two existing
 * predicates, not a new evaluator).
 *
 * <p>Order, every time:
 * <ol>
 *   <li>read the tenant's current node from the authority (404 if the tenant does not exist);</li>
 *   <li>authorize SOURCE, then DESTINATION (404 + best-effort DENIED row);</li>
 *   <li>only then talk about the target — the no-op check happens at the authority, after
 *       authorization, so "is T already under D?" is never answered to a non-administrator;</li>
 *   <li>write conditionally on the source that was authorized ({@code expectedOrgNodeId}) — a
 *       concurrent move turns into 409, never into moving T out of a node nobody checked.</li>
 * </ol>
 *
 * <p><b>No {@code @Transactional}</b>, same reasoning as {@link OrgNodeAdminUseCase}: every
 * method makes remote calls, and a remote call must not hold a DB transaction open. The
 * auditor manages its own persistence.
 */
@Service
@RequiredArgsConstructor
public class TenantOrgNodePlacementUseCase {

    private final TenantPlacementPort placementPort;
    private final OrgNodePort orgNodePort;
    private final OrgNodeScopeGuard orgNodeScopeGuard;
    private final OrgNodeSubtreeResolver subtreeResolver;
    private final AdminActionAuditor auditor;
    private final CreateTenantUseCase createTenantUseCase;

    /**
     * Rider P2 — what the move would do to the tenant's effective domains, before the confirm
     * click. Same two-sided check as the write: a preview must not read another tenant's domains.
     * No audit row on success (read path); a refusal writes the best-effort DENIED row.
     */
    public TenantPlacementView preview(OperatorContext actor, String tenantId, String targetOrgNodeId) {
        String target = blankToNull(targetOrgNodeId);
        authorize(actor, tenantId, target);
        return placementPort.preview(tenantId, target);
    }

    /**
     * Moves the tenant. {@code targetOrgNodeId == null} detaches it.
     *
     * @throws com.example.admin.application.exception.TenantNotFoundException        tenant unknown, or SOURCE not administered
     * @throws com.example.admin.application.exception.OrgNodeNotFoundException       target unknown / not administered / deleted meanwhile
     * @throws com.example.admin.application.exception.TenantOrgNodeConflictException placement changed between check and write
     */
    public TenantPlacementView place(OperatorContext actor, String tenantId, String targetOrgNodeId, String reason) {
        String target = blankToNull(targetOrgNodeId);
        String authorizedSource = authorize(actor, tenantId, target);

        TenantPlacementView result = placementPort.place(tenantId, target, authorizedSource);

        // The subtree driver of effectiveAdminScope now differs for the from/to nodes' admins.
        subtreeResolver.invalidateAll();
        audit(actor, tenantId, result, reason);
        return result;
    }

    /**
     * {@code POST /api/admin/tenants} with an {@code orgNodeId}: the same write rule, with the
     * source "ungrouped" (the tenant does not exist yet) and the creator already a platform actor
     * (the caller enforces {@code tenant.manage} + platform scope).
     *
     * <p>The DESTINATION is checked <b>before</b> anything is created — an out-of-reach node
     * leaves no tenant behind. Creation and placement are then two authority calls; see the
     * contract's ordering note for the one race between them.
     */
    public TenantSummary createTenantUnder(OperatorContext actor, String tenantId, String displayName,
                                           String tenantType, String orgNodeId,
                                           String reason, String idempotencyKey) {
        String target = blankToNull(orgNodeId);
        OrgNodeScopeGuard.Reach reach = orgNodeScopeGuard.resolveReach(actor, orgNodePort.list());
        orgNodeScopeGuard.requirePlacementAllowed(actor, reach, tenantId, null, target,
                ActionCode.TENANT_ORG_NODE_ASSIGN);

        TenantSummary created = createTenantUseCase.execute(
                tenantId, displayName, tenantType, actor, reason, idempotencyKey);

        if (target != null) {
            TenantPlacementView placed = placementPort.place(tenantId, target, null);
            subtreeResolver.invalidateAll();
            audit(actor, tenantId, placed, reason);
        }
        return created;
    }

    // ---- internals -------------------------------------------------------------

    /** @return the source that was authorized — the write is made conditional on it */
    private String authorize(OperatorContext actor, String tenantId, String target) {
        String current = placementPort.currentOrgNodeId(tenantId);
        if (current == null) {
            // Rider P1 reads effectiveAdminScope, whose subtree driver is cached for a few
            // seconds. An ungrouped tenant is in no ORG_ADMIN subtree — but a tenant detached a
            // moment ago may still sit in a cached one. Drop the cache so the SOURCE check of an
            // ungrouped tenant sees only SUPER_ADMIN and the tenant's own TENANT_ADMIN.
            subtreeResolver.invalidateAll();
        }
        OrgNodeScopeGuard.Reach reach = orgNodeScopeGuard.resolveReach(actor, orgNodePort.list());
        orgNodeScopeGuard.requirePlacementAllowed(actor, reach, tenantId, current, target,
                ActionCode.TENANT_ORG_NODE_ASSIGN);
        return current;
    }

    private void audit(OperatorContext actor, String tenantId, TenantPlacementView result, String reason) {
        String auditId = auditor.newAuditId();
        Instant now = Instant.now();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.TENANT_ORG_NODE_ASSIGN,
                actor,
                "TENANT",
                tenantId,
                AuditReasons.normalize(reason),
                null,
                "tenant-placement:" + auditId,
                Outcome.SUCCESS,
                "from_org_node_id=" + result.fromOrgNodeId()
                        + " to_org_node_id=" + result.toOrgNodeId()
                        + " changed=" + result.changed(),
                now,
                Instant.now(),
                tenantId));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
