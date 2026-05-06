package com.example.scmplatform.procurement.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * Service-level fail-closed re-validation of {@code tenant_id} (TASK-SCM-BE-002 §
 * Acceptance Criteria #7-#8).
 *
 * <p>Mirrors fan-platform's community-service validator: even if a request
 * bypasses the gateway or a future gateway loosening widens the tenant set,
 * this validator still rejects cross-tenant traffic at decode time.
 *
 * <p>{@code "*"} wildcard is allowed for SUPER_ADMIN platform-scope tokens.
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
                    ERROR_CODE_TENANT_MISMATCH,
                    "tenant_id claim is required",
                    null));
        }
        if (WILDCARD_TENANT.equals(tenantId)) {
            return OAuth2TokenValidatorResult.success();
        }
        if (!expectedTenantId.equals(tenantId)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE_TENANT_MISMATCH,
                    "tenant_id '" + tenantId + "' is not allowed",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
