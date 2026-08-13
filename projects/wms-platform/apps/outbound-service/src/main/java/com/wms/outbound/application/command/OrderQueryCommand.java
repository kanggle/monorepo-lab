package com.wms.outbound.application.command;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filter inputs for the paginated list endpoint
 * {@code GET /api/v1/outbound/orders}. All fields are optional.
 *
 * <p>{@code tenantId} is the cross-tenant isolation filter (TASK-MONO-304):
 * NULL for an unrestricted (native wms / platform / internal) caller, and the
 * customer tenant id for a tenant-scoped caller — applied server-side from the
 * signed JWT, never from a client query param. See
 * {@link com.wms.outbound.application.security.CallerScope#scopeListQuery}.
 */
public record OrderQueryCommand(
        String status,
        UUID warehouseId,
        UUID customerPartnerId,
        String source,
        String orderNo,
        String tenantId,
        LocalDate requiredShipAfter,
        LocalDate requiredShipBefore,
        Instant createdAfter,
        Instant createdBefore,
        int page,
        int size
) {

    /**
     * Returns a copy pinned to a single customer tenant: {@code tenantId} is set
     * from the caller's SIGNED claim and every other filter — including
     * {@code source} — is left exactly as the client supplied it.
     *
     * <p><b>ADR-MONO-064 § D2 removed the {@code source} override.</b> This method
     * used to take a {@code source} argument and force it to
     * {@code FULFILLMENT_ECOMMERCE}, on the reasoning that a tenant-scoped caller
     * may only ever see its own ecommerce orders. That pin was <em>redundant</em>
     * before § D1: {@code tenant_id} was populated only on the fulfilment path, so
     * pinning the tenant already implied the source. After § D1 stamps the caller's
     * tenant onto the orders that caller creates, the same pin excludes precisely
     * the orders this decision exists to reveal — a {@code demo-corp} operator's own
     * {@code MANUAL} order is tenant-owned and would still be filtered out by it.
     *
     * <p><b>The isolation axis is {@code tenant_id}, and only {@code tenant_id}.</b>
     * Dropping the source pin widens what a caller sees <em>within its own tenant</em>;
     * it widens nothing <em>across</em> tenants, which is the property TASK-MONO-304
     * installed and which {@code CallerScopeTest} / {@code OrderQueryServiceTest}
     * keep pinned.
     */
    public OrderQueryCommand withTenantScope(String tenantId) {
        return new OrderQueryCommand(
                status, warehouseId, customerPartnerId, source, orderNo, tenantId,
                requiredShipAfter, requiredShipBefore, createdAfter, createdBefore,
                page, size);
    }
}
