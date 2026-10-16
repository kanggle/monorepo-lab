package com.example.promotion.application.service;

import com.example.web.exception.AccessDeniedException;
import com.example.web.security.RoleListHeader;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8) — the promotion area's business-role decision for the operator plane. Canonical
 * table: ecommerce {@code iam-integration.md} § 업무 역할.
 *
 * <ul>
 *   <li>read = {@code ECOMMERCE_PROMOTION_READ ∨ ECOMMERCE_PROMOTION_WRITE};</li>
 *   <li>write = {@code ECOMMERCE_PROMOTION_WRITE}.</li>
 * </ul>
 * The entry role {@code ECOMMERCE_OPERATOR} opens nothing here — it only gets a token through the gateway. The
 * platform wildcard ({@code '*'}, no roles) is not admitted — as before this ticket. Reads: promotion detail · list · summary; writes: create · update · delete · coupon issue. The header comparison
 * rule (comma-joined, trimmed, case-insensitive, whole-element) is {@link RoleListHeader}'s — never a local split
 * ({@code RoleHeaderCopyGuardTest}).
 */
final class OperatorRoleGuard {

    static final String PROMOTION_READ = "ECOMMERCE_PROMOTION_READ";
    static final String PROMOTION_WRITE = "ECOMMERCE_PROMOTION_WRITE";

    private OperatorRoleGuard() {
    }

    static void requireRead(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, PROMOTION_READ, PROMOTION_WRITE)) {
            throw new AccessDeniedException();
        }
    }

    static void requireWrite(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, PROMOTION_WRITE)) {
            throw new AccessDeniedException();
        }
    }
}
