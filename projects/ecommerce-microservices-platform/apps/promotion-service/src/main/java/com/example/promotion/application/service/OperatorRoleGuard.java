package com.example.promotion.application.service;

import com.example.web.exception.AccessDeniedException;

/**
 * Operator-role guard for the promotion admin-plane services. Verifies the gateway-trusted
 * {@code X-User-Role} carries {@code ECOMMERCE_OPERATOR} (comma-separated, case-insensitive) —
 * otherwise {@link AccessDeniedException} (403). Single home for the authorization check the
 * coupon/promotion command + query services previously copy-pasted.
 */
final class OperatorRoleGuard {

    private static final String OPERATOR_ROLE = "ECOMMERCE_OPERATOR";

    private OperatorRoleGuard() {
    }

    static void requireOperator(String userRole) {
        if (!hasOperatorRole(userRole)) {
            throw new AccessDeniedException();
        }
    }

    private static boolean hasOperatorRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if (OPERATOR_ROLE.equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
