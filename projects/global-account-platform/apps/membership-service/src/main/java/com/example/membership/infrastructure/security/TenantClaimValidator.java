package com.example.membership.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * Rejects access tokens whose {@code tenant_id} claim does not match the expected
 * tenant for this service.
 *
 * <p>TASK-BE-253: membership-service is a {@code fan-platform} consumer. Tokens
 * issued for any other tenant ({@code wms}, future {@code erp}/...) MUST be
 * rejected. Combined with the standard issuer + signature + expiration validators
 * provided by Spring Security, this is a defense-in-depth check that protects the
 * service when GAP issues a legitimately signed token belonging to a different
 * tenant.
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    /** Error code surfaced to the entry point. */
    public static final String ERROR_CODE_TENANT_MISMATCH = "tenant_mismatch";

    public static final String CLAIM_TENANT_ID = "tenant_id";

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
        if (!expectedTenantId.equals(tenantId)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE_TENANT_MISMATCH,
                    "tenant_id '" + tenantId + "' is not allowed",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
