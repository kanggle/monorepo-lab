package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a tenant-scoped caller (an ecommerce-assumed operator admitted
 * via the {@code entitled_domains} dual-accept, whose {@code tenant_id} claim
 * is NOT the native wms tenant) attempts to read or mutate an outbound order
 * that does not belong to that caller's tenant. Mapped to {@code 403} with
 * code {@code TENANT_SCOPE_DENIED} (TASK-MONO-304 / ADR-MONO-022 § D9).
 *
 * <p>The client-facing {@link #getMessage()} is intentionally generic (no
 * resource-tenant disclosure); the resolved ids are kept as fields for
 * server-side logging only.
 */
public class TenantScopeDeniedException extends OutboundDomainException {

    private final UUID resourceId;
    private final String callerTenantId;
    private final String resourceTenantId;

    public TenantScopeDeniedException(UUID resourceId, String callerTenantId, String resourceTenantId) {
        super("Order is not accessible within the caller's tenant scope");
        this.resourceId = resourceId;
        this.callerTenantId = callerTenantId;
        this.resourceTenantId = resourceTenantId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getCallerTenantId() {
        return callerTenantId;
    }

    public String getResourceTenantId() {
        return resourceTenantId;
    }

    @Override
    public String errorCode() {
        return "TENANT_SCOPE_DENIED";
    }
}
