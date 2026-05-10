package com.wms.outbound.application.service;

import java.util.Arrays;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

/**
 * Package-private static utility: authorization guards shared across
 * application services in this package.
 *
 * <p>Not a Spring bean — pure functions, no state. All methods throw
 * {@link AccessDeniedException} on failure, preserving the exact behaviour
 * that was previously inlined in each service.
 */
final class AuthorizationGuards {

    private AuthorizationGuards() {
        // utility class — no instances
    }

    /**
     * Asserts that {@code roles} contains {@code required}; throws
     * {@link AccessDeniedException} otherwise.
     *
     * @param roles    the caller's role set (may be {@code null})
     * @param required the single role that must be present
     */
    static void requireRole(Set<String> roles, String required) {
        if (roles == null || !roles.contains(required)) {
            throw new AccessDeniedException("Role required: " + required);
        }
    }

    /**
     * Asserts that {@code roles} contains at least one of {@code required};
     * throws {@link AccessDeniedException} otherwise.
     *
     * @param roles    the caller's role set (may be {@code null})
     * @param required one or more roles, any of which is sufficient
     */
    static void requireAnyRole(Set<String> roles, String... required) {
        if (roles == null) {
            throw new AccessDeniedException("Role required: any of " + Arrays.toString(required));
        }
        for (String r : required) {
            if (roles.contains(r)) {
                return;
            }
        }
        throw new AccessDeniedException("Role required: any of " + Arrays.toString(required));
    }
}
