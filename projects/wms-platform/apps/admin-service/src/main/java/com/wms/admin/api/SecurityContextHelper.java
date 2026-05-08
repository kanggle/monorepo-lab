package com.wms.admin.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helpers for reading the current authenticated user's roles. Used by
 * controllers to forward role context (e.g., {@code callerIsSuperadmin}) into
 * application-layer use-cases without coupling those use-cases to Spring
 * Security types.
 */
public final class SecurityContextHelper {

    public static final String ROLE_SUPERADMIN = "ROLE_WMS_SUPERADMIN";

    private SecurityContextHelper() {}

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    public static boolean isSuperadmin() {
        return hasRole(ROLE_SUPERADMIN);
    }
}
