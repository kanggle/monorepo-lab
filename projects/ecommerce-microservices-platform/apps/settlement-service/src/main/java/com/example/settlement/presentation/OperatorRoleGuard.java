package com.example.settlement.presentation;

import com.example.web.exception.AccessDeniedException;
import com.example.web.security.RoleListHeader;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8) — the settlement area's business-role decision for the operator plane. Canonical
 * table: ecommerce {@code iam-integration.md} § 업무 역할.
 *
 * <ul>
 *   <li>read = {@code ECOMMERCE_SETTLEMENT_READ ∨ ECOMMERCE_SETTLEMENT_WRITE};</li>
 *   <li>write = {@code ECOMMERCE_SETTLEMENT_WRITE}.</li>
 * </ul>
 * The entry role {@code ECOMMERCE_OPERATOR} opens nothing here — it only gets a token through the gateway. The
 * platform wildcard ({@code '*'}, no roles) is not admitted — as before this ticket. Payout execution and period close are WRITE (owner decision Q2 — no separate control role). The header comparison
 * rule (comma-joined, trimmed, case-insensitive, whole-element) is {@link RoleListHeader}'s — never a local split
 * ({@code RoleHeaderCopyGuardTest}).
 */
final class OperatorRoleGuard {

    static final String SETTLEMENT_READ = "ECOMMERCE_SETTLEMENT_READ";
    static final String SETTLEMENT_WRITE = "ECOMMERCE_SETTLEMENT_WRITE";

    private OperatorRoleGuard() {
    }

    static void requireRead(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, SETTLEMENT_READ, SETTLEMENT_WRITE)) {
            throw new AccessDeniedException();
        }
    }

    static void requireWrite(String userRole) {
        if (!RoleListHeader.holdsAny(userRole, SETTLEMENT_WRITE)) {
            throw new AccessDeniedException();
        }
    }
}
