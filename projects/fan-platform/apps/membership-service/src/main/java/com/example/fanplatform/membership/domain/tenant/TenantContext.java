package com.example.fanplatform.membership.domain.tenant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Resolves the current request's {@code tenant_id} from the validated JWT in the
 * Spring SecurityContext (end-user routes only). Mirrors the community-service
 * convention. Not used by the {@code /internal/**} chain, where {@code tenantId}
 * is an explicit query parameter.
 */
public final class TenantContext {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String DEFAULT_TENANT_ID = "fan-platform";
    public static final String WILDCARD_TENANT = "*";

    private TenantContext() {
    }

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
        throw new IllegalStateException("Unsupported principal type: " + auth.getClass().getName());
    }

    /**
     * Resolves the effective tenant for query scoping. Wildcard {@code "*"}
     * (SUPER_ADMIN platform-scope) maps to the project's default tenant.
     */
    public static String effectiveTenant() {
        String t = currentTenantOrThrow();
        return WILDCARD_TENANT.equals(t) ? DEFAULT_TENANT_ID : t;
    }
}
