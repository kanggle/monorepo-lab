package com.example.order.presentation;

import com.example.web.exception.AccessDeniedException;
import com.example.web.security.RoleListHeader;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8) — the order area's business-role decision for the operator plane
 * ({@code /api/admin/orders/**}). Canonical table: ecommerce {@code iam-integration.md} § 업무 역할.
 *
 * <ul>
 *   <li>read = {@code ECOMMERCE_ORDER_READ ∨ ECOMMERCE_ORDER_WRITE}, and never the wildcard tenant
 *       ({@code X-Tenant-Id = '*'}). TASK-BE-506 had let {@code '*'} through this gate, but no data path below
 *       ever read across tenants — {@code '*'} read tenant {@code '*'} (no rows), so every read answered zeros, an
 *       empty page or 404. TASK-MONO-817 refused it on {@code /metrics}; TASK-BE-659 refuses it on every read
 *       (owner decision 2026-10-25 UTC — no cross-tenant order read is built). The console does not reach these
 *       with {@code '*'} (TASK-MONO-817 AC-0).</li>
 *   <li>write = {@code ECOMMERCE_ORDER_WRITE}.</li>
 * </ul>
 * The entry role {@code ECOMMERCE_OPERATOR} opens nothing here — it only gets a token through the gateway. The header
 * comparison rule is {@link RoleListHeader}'s (never a local split — {@code RoleHeaderCopyGuardTest}).
 */
final class OrderOperatorAccess {

    static final String ORDER_READ = "ECOMMERCE_ORDER_READ";
    static final String ORDER_WRITE = "ECOMMERCE_ORDER_WRITE";

    /** The wildcard tenant the gateway injects for a platform SUPER_ADMIN token. */
    static final String WILDCARD_TENANT = "*";

    private OrderOperatorAccess() {
    }

    /**
     * The wildcard tenant is refused whatever the roles: tenant {@code *} owns no order, so any read would answer
     * nothing as if that were the truth ({@code performance-metrics-contract.md} § Wildcard tenant — «조용한 0 금지»).
     */
    static void requireRead(String userRole, String tenantId) {
        if (WILDCARD_TENANT.equals(tenantId) || !RoleListHeader.holdsAny(userRole, ORDER_READ, ORDER_WRITE)) {
            throw new AccessDeniedException();
        }
    }

    static void requireWrite(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, ORDER_WRITE)) {
            throw new AccessDeniedException();
        }
    }
}
