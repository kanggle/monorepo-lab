package com.example.product.presentation.controller;

import com.example.web.exception.AccessDeniedException;
import com.example.web.security.RoleListHeader;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8 · owner decision Q1) — the product area's business-role decision for the operator
 * plane: products · variants · stock · images ({@code /api/admin/products/**}), sellers and seller members
 * ({@code /api/admin/sellers/**}) — and search reindex, which search-service decides with the same role name.
 * Canonical table: ecommerce {@code iam-integration.md} § 업무 역할.
 *
 * <ul>
 *   <li>read = {@code ECOMMERCE_PRODUCT_READ ∨ ECOMMERCE_PRODUCT_WRITE};</li>
 *   <li>{@link #requireReadOrWildcard} additionally admits the platform wildcard read
 *       ({@code X-Tenant-Id = '*'}, TASK-BE-506) — used ONLY on the product and image reads, where it reached before
 *       this ticket; the seller reads never admitted it and still do not;</li>
 *   <li>write = {@code ECOMMERCE_PRODUCT_WRITE}.</li>
 * </ul>
 * The entry role {@code ECOMMERCE_OPERATOR} opens nothing here. The header comparison rule is {@link RoleListHeader}'s.
 */
final class ProductOperatorAccess {

    static final String PRODUCT_READ = "ECOMMERCE_PRODUCT_READ";
    static final String PRODUCT_WRITE = "ECOMMERCE_PRODUCT_WRITE";

    /** The wildcard tenant the gateway injects for a platform SUPER_ADMIN token. */
    static final String WILDCARD_TENANT = "*";

    private ProductOperatorAccess() {
    }

    static void requireRead(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, PRODUCT_READ, PRODUCT_WRITE)) {
            throw new AccessDeniedException();
        }
    }

    static void requireReadOrWildcard(String userRole, String tenantId) {
        if (!WILDCARD_TENANT.equals(tenantId)) {
            requireRead(userRole);
        }
    }

    static void requireWrite(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, PRODUCT_WRITE)) {
            throw new AccessDeniedException();
        }
    }
}
