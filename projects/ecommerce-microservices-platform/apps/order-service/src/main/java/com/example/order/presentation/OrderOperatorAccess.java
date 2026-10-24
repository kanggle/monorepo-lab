package com.example.order.presentation;

import com.example.web.exception.AccessDeniedException;
import com.example.web.security.RoleListHeader;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8) — the order area's business-role decision for the operator plane
 * ({@code /api/admin/orders/**}). Canonical table: ecommerce {@code iam-integration.md} § 업무 역할.
 *
 * <ul>
 *   <li>read = {@code ECOMMERCE_ORDER_READ ∨ ECOMMERCE_ORDER_WRITE}, or the platform wildcard read
 *       ({@code X-Tenant-Id = '*'} — the role-less SUPER_ADMIN domain token the console operator overview forwards,
 *       TASK-BE-506; kept exactly as before this ticket);</li>
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

    static void requireRead(String userRole, String tenantId) {
        if (!RoleListHeader.holdsAny(userRole, ORDER_READ, ORDER_WRITE) && !WILDCARD_TENANT.equals(tenantId)) {
            throw new AccessDeniedException();
        }
    }

    /**
     * TASK-MONO-817 — the overview metrics refuse the wildcard tenant. Tenant {@code *} owns no order, so the
     * figures would be all {@code "0"}, which a client renders as «nothing happened»
     * ({@code performance-metrics-contract.md} § Wildcard tenant). Applies whatever the roles; the other reads keep
     * the wildcard read above.
     */
    static void requireConcreteTenant(String tenantId) {
        if (WILDCARD_TENANT.equals(tenantId)) {
            throw new AccessDeniedException();
        }
    }

    static void requireWrite(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, ORDER_WRITE)) {
            throw new AccessDeniedException();
        }
    }
}
