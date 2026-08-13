package com.wms.outbound.application.security;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.domain.exception.TenantScopeDeniedException;
import java.util.UUID;

/**
 * The outbound-order visibility scope of the current caller, derived from the
 * authenticated JWT (TASK-MONO-304 / ADR-MONO-022 § D9, amended by
 * ADR-MONO-064).
 *
 * <p>Two shapes:
 * <ul>
 *   <li><b>unrestricted</b> — a native wms operator ({@code tenant_id=wms}) or an
 *       unauthenticated internal flow (Kafka consumer / scheduler / no security
 *       context). Sees every outbound order.</li>
 *   <li><b>restricted</b> — every other authenticated caller, i.e. a customer-tenant
 *       operator (e.g. {@code tenant_id=demo-corp} or {@code ecommerce}, admitted to
 *       wms via the {@code entitled_domains} dual-accept). Sees ONLY its own tenant's
 *       orders; any other order yields 403.</li>
 * </ul>
 *
 * <h2>ADR-MONO-064 changed two sentences that used to stand here</h2>
 *
 * <p><b>The platform wildcard is no longer a caller shape (§ D3).</b> This javadoc
 * used to name "a platform-scope operator ({@code tenant_id=*})" as unrestricted.
 * wms's own admission gate <em>refuses</em> that token by deliberate decision
 * (ADR-MONO-048 § D5 — wms is the only platform that does), so the application layer
 * was extending unrestricted visibility to precisely the identity the edge was written
 * to turn away. A {@code "*"} token that reaches here (it can be admitted on its
 * {@code entitled_domains} rather than on the wildcard) is now
 * {@code restrictedTo("*")}, and no order carries that string, so it sees nothing.
 *
 * <p><b>{@code tenant_id} is no longer ecommerce-only (§ D1).</b> The old text said the
 * isolation key "is populated ONLY for {@code FULFILLMENT_ECOMMERCE} orders … so a
 * restricted caller can never match a non-ecommerce order" — and that was the defect,
 * not the design: a tenant-scoped operator could create an order (the create path is
 * the one operation with no prior order to check) and then could not read, pick, pack,
 * ship or even cancel it. The create path now stamps the caller's signed tenant, so a
 * restricted caller owns what it creates, and the list path no longer pins
 * {@code source} (see {@link OrderQueryCommand#withTenantScope}).
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
     * pinned to {@code tenantId == this.tenantId} — and to nothing else
     * (ADR-MONO-064 § D2; the {@code source == FULFILLMENT_ECOMMERCE} override
     * this used to apply is gone, and {@link OrderQueryCommand#withTenantScope}
     * carries the reasoning).
     */
    public OrderQueryCommand scopeListQuery(OrderQueryCommand command) {
        if (!restricted) {
            return command;
        }
        return command.withTenantScope(tenantId);
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
