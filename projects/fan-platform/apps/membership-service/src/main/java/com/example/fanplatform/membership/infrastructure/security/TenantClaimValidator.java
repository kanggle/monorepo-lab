package com.example.fanplatform.membership.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * Service-level fail-closed re-validation of {@code tenant_id} on the end-user
 * surface. The fan-platform gateway already validates this claim, but the
 * service re-validates so a gateway bypass (mis-routed / internal-network /
 * future direct caller) is still rejected, and any future loosening of the
 * gateway cannot silently widen the service's effective tenant set.
 *
 * <p>Wildcard {@code "*"} is allowed (SUPER_ADMIN platform-scope) — same
 * exception the gateway makes.
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    public static final String ERROR_CODE_TENANT_MISMATCH = "tenant_mismatch";
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String WILDCARD_TENANT = "*";

    private final String expectedTenantId;

    public TenantClaimValidator(String expectedTenantId) {
        this.expectedTenantId = Objects.requireNonNull(expectedTenantId, "expectedTenantId");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object raw = jwt.getClaim(CLAIM_TENANT_ID);
        String tenantId = raw instanceof String s ? s : null;
        if (tenantId == null || tenantId.isBlank()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE_TENANT_MISMATCH, "tenant_id claim is required", null));
        }
        if (WILDCARD_TENANT.equals(tenantId)) {
            return OAuth2TokenValidatorResult.success();
        }
        if (!expectedTenantId.equals(tenantId)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE_TENANT_MISMATCH,
                    "tenant_id '" + tenantId + "' is not allowed", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
