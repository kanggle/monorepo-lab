package com.example.fanplatform.artist.domain.tenant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Resolves the current request's {@code tenant_id} from the validated JWT in
 * Spring SecurityContext.
 *
 * <p>Mirrors community-service's {@code TenantContext}. Intentionally NOT a
 * ThreadLocal — Spring already manages the SecurityContext per request.
 * Reading from there guarantees the value matches what the framework's
 * validators accepted, which is the entire point of the service-level
 * fail-closed re-validation.
 */
public final class TenantContext {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String DEFAULT_TENANT_ID = "fan-platform";
    public static final String WILDCARD_TENANT = "*";

    private TenantContext() {}

    /**
     * @return the {@code tenant_id} claim of the authenticated principal.
     * @throws IllegalStateException when no JWT principal exists or the claim
     *                               is absent. The Spring Security filter chain
     *                               normally rejects such requests before the
     *                               controller, so this should never fire in
     *                               practice — defensive guard.
     */
    public static String currentTenantOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated principal");
        }
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String tenantId = jwt.getClaimAsString(CLAIM_TENANT_ID);
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalStateException("tenant_id claim missing on principal");
            }
            return tenantId;
        }
        throw new IllegalStateException("Unsupported principal type: "
                + auth.getClass().getName());
    }

    /**
     * Resolves the effective tenant for query scoping. Wildcard {@code "*"}
     * (SUPER_ADMIN platform-scope) is mapped to the project's default tenant
     * so the service still operates over its own data set.
     */
    public static String effectiveTenant() {
        String t = currentTenantOrThrow();
        return WILDCARD_TENANT.equals(t) ? DEFAULT_TENANT_ID : t;
    }
}
