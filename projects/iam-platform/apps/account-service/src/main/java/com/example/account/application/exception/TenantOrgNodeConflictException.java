package com.example.account.application.exception;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07): a placement write named the org-node the
 * caller authorized against ({@code expectedOrgNodeId}), and the tenant is no longer there —
 * another write moved it in between. Nothing was written. Surfaces as 409
 * {@code TENANT_ORG_NODE_CONFLICT}.
 *
 * <p>This is what keeps the two-sided check honest across the service boundary: admin-service
 * decides "the actor administers the SOURCE", and the write only lands if that source is still
 * the source.
 */
public class TenantOrgNodeConflictException extends RuntimeException {

    public TenantOrgNodeConflictException(String tenantId, String expectedOrgNodeId, String actualOrgNodeId) {
        super("Tenant '" + tenantId + "' placement changed: expected org node "
                + expectedOrgNodeId + " but found " + actualOrgNodeId);
    }
}
