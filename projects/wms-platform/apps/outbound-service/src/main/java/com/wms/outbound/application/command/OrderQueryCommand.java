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
     * Returns a copy pinned to a customer tenant's ecommerce-fulfilment orders:
     * {@code tenantId} is set and {@code source} is overridden to
     * {@code FULFILLMENT_ECOMMERCE} (a tenant-scoped caller may only ever see
     * its own ecommerce orders, regardless of any client-supplied source).
     */
    public OrderQueryCommand withTenantScope(String tenantId, String source) {
        return new OrderQueryCommand(
                status, warehouseId, customerPartnerId, source, orderNo, tenantId,
                requiredShipAfter, requiredShipBefore, createdAfter, createdBefore,
                page, size);
    }
}
