package com.wms.outbound.application.security;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.domain.exception.TenantScopeDeniedException;
import com.wms.outbound.domain.model.OrderSource;
import java.util.UUID;

/**
 * The outbound-order visibility scope of the current caller, derived from the
 * authenticated JWT (TASK-MONO-304 / ADR-MONO-022 § D9).
 *
 * <p>Two shapes:
 * <ul>
 *   <li><b>unrestricted</b> — a native wms operator ({@code tenant_id=wms}),
 *       a platform-scope operator ({@code tenant_id=*}), or an unauthenticated
 *       internal flow (Kafka consumer / no security context). Sees every
 *       outbound order; behaviour is unchanged from before this feature.</li>
 *   <li><b>restricted</b> — a customer-tenant operator (e.g. an ecommerce
 *       operator with {@code tenant_id=ecommerce}, admitted to wms via the
 *       {@code entitled_domains} dual-accept). Sees ONLY their own tenant's
 *       {@code FULFILLMENT_ECOMMERCE} orders; any other order yields 403.</li>
 * </ul>
 *
 * <p>The isolation key is {@code OrderEntity.tenant_id}, which is populated
 * ONLY for {@code FULFILLMENT_ECOMMERCE} orders (ADR-MONO-022 facet d) and is
 * NULL for B2B / standalone orders — so a restricted caller can never match a
 * non-ecommerce order. The list path additionally pins
 * {@code source=FULFILLMENT_ECOMMERCE} to make the policy explicit.
 */
public final class CallerScope {

    private final boolean restricted;
    private final String tenantId;

    private CallerScope(boolean restricted, String tenantId) {
        this.restricted = restricted;
        this.tenantId = tenantId;
    }

    /** A caller that may see every outbound order (native wms / platform / internal). */
    public static CallerScope unrestricted() {
        return new CallerScope(false, null);
    }

    /** A caller scoped to a single customer tenant's ecommerce-fulfilment orders. */
    public static CallerScope restrictedTo(String tenantId) {
        return new CallerScope(true, tenantId);
    }

    public boolean isRestricted() {
        return restricted;
    }

    public String tenantId() {
        return tenantId;
    }

    /**
     * Returns a list-query command scoped to this caller. For an unrestricted
     * caller the command is returned unchanged; for a restricted caller it is
     * pinned to {@code tenantId == this.tenantId} AND
     * {@code source == FULFILLMENT_ECOMMERCE} (overriding any client-supplied
     * source filter).
     */
    public OrderQueryCommand scopeListQuery(OrderQueryCommand command) {
        if (!restricted) {
            return command;
        }
        return command.withTenantScope(tenantId, OrderSource.FULFILLMENT_ECOMMERCE.name());
    }

    /**
     * Enforces that this caller may access the order identified by
     * {@code orderId} whose persisted tenant is {@code orderTenantId}.
     * No-op for an unrestricted caller; throws {@link TenantScopeDeniedException}
     * (403) for a restricted caller whose tenant does not match
     * {@code orderTenantId} (including the {@code orderTenantId == null} B2B case).
     */
    public void requireOrderAccess(String orderTenantId, UUID orderId) {
        if (!restricted) {
            return;
        }
        if (tenantId != null && tenantId.equals(orderTenantId)) {
            return;
        }
        throw new TenantScopeDeniedException(orderId, tenantId, orderTenantId);
    }
}
