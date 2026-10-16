package com.example.shipping.application.service;

import com.example.web.exception.AccessDeniedException;
import com.example.web.security.RoleListHeader;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8) — the shipping area's business-role decision for the operator plane. Canonical
 * table: ecommerce {@code iam-integration.md} § 업무 역할.
 *
 * <ul>
 *   <li>read = {@code ECOMMERCE_SHIPPING_READ ∨ ECOMMERCE_SHIPPING_WRITE};</li>
 *   <li>write = {@code ECOMMERCE_SHIPPING_WRITE}.</li>
 * </ul>
 * The entry role {@code ECOMMERCE_OPERATOR} opens nothing here — it only gets a token through the gateway. The
 * platform wildcard ({@code '*'}, no roles) is not admitted — as before this ticket. Reads: the operator list · summary; writes: status change · tracking refresh. The buyer's own shipping read and the carrier webhook are not operator paths and never reach this guard. The header comparison
 * rule (comma-joined, trimmed, case-insensitive, whole-element) is {@link RoleListHeader}'s — never a local split
 * ({@code RoleHeaderCopyGuardTest}).
 */
final class OperatorRoleGuard {

    static final String SHIPPING_READ = "ECOMMERCE_SHIPPING_READ";
    static final String SHIPPING_WRITE = "ECOMMERCE_SHIPPING_WRITE";

    private OperatorRoleGuard() {
    }

    static void requireRead(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, SHIPPING_READ, SHIPPING_WRITE)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    static void requireWrite(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, SHIPPING_WRITE)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
