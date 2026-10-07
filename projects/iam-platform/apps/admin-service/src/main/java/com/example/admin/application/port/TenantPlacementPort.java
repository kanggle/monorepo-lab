package com.example.admin.application.port;

import com.example.admin.application.orgnode.TenantPlacementView;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07) — the outbound port onto account-service's
 * tenant-placement authority ({@code /internal/tenant-placements/**}). admin-service decides
 * <em>who</em> may move a tenant; the authority knows what exists, writes conditionally, and
 * computes the effect.
 *
 * <p><b>Failure contract.</b> 404 {@code TENANT_NOT_FOUND} →
 * {@link com.example.admin.application.exception.TenantNotFoundException}; 404
 * {@code ORG_NODE_NOT_FOUND} → {@link com.example.admin.application.exception.OrgNodeNotFoundException};
 * 409 → {@link com.example.admin.application.exception.TenantOrgNodeConflictException};
 * 5xx / timeout → {@link com.example.admin.application.exception.DownstreamFailureException}
 * (circuit-open is rethrown untouched).
 */
public interface TenantPlacementPort {

    /** @return the tenant's current org-node id, {@code null} when ungrouped */
    String currentOrgNodeId(String tenantId);

    /** The effect of moving the tenant to {@code targetOrgNodeId} ({@code null} = detach). No write. */
    TenantPlacementView preview(String tenantId, String targetOrgNodeId);

    /**
     * Moves the tenant — but only if it is still at {@code expectedOrgNodeId}, the source the
     * actor was authorized against.
     */
    TenantPlacementView place(String tenantId, String targetOrgNodeId, String expectedOrgNodeId);
}
