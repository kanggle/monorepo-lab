package com.example.gateway.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * Rejects access tokens whose {@code tenant_id} claim does not match the expected
 * tenant for this gateway.
 *
 * <p>TASK-MONO-027: gateway-service is the {@code ecommerce} edge. Tokens issued
 * for any other tenant ({@code wms}, {@code fan-platform}, future
 * {@code erp}/{@code scm}/...) MUST be rejected at the edge so cross-tenant
 * tokens never reach internal services. The validator raises a granular error
 * code so the
 * {@link org.springframework.security.web.server.ServerAuthenticationEntryPoint}
 * can map cross-tenant misuse to 403 ({@code TENANT_FORBIDDEN}).
 *
 * <p>Mirrors the wms-platform implementation (TASK-MONO-019) byte-for-byte —
 * only the package and the configured tenant differ. The validator is stateless
 * and reactive-safe (used by Spring Cloud Gateway's NimbusReactiveJwtDecoder).
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

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
