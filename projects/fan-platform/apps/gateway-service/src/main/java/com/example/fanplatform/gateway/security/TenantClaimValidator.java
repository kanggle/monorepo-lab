package com.example.fanplatform.gateway.security;

import com.example.apigateway.security.GatewayErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * Rejects access tokens whose {@code tenant_id} claim does not match the expected
 * tenant for this gateway.
 *
 * <p>fan-platform gateway is the {@code fan-platform} edge. Tokens issued for any
 * other tenant ({@code wms}, {@code ecommerce}, future {@code erp}/{@code scm}/...)
 * MUST be rejected at the edge so cross-tenant tokens never reach internal services.
 *
 * <p>SUPER_ADMIN exception: tokens issued for platform-scope SUPER_ADMINs carry
 * {@code tenant_id="*"} (wildcard). They are accepted regardless of the
 * {@code expectedTenantId} so platform operators can hit any tenant's edge for
 * incident response. See task TASK-FAN-BE-001 § Failure Scenarios.
 *
 * <p>The validator raises a granular error code so the
 * {@link org.springframework.security.web.server.ServerAuthenticationEntryPoint}
 * can map cross-tenant misuse to 403 ({@code TENANT_FORBIDDEN}).
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    /**
     * Same value it has always had, now sourced from the shared library so the code this
     * validator <em>raises</em> and the code the shared {@code SecurityConfig} maps to 403
     * cannot drift apart (TASK-MONO-351). This class itself stays per-domain — the tenant
     * gate policy differs across gateways — until ADR-MONO-048 D7 step 2.
     */
    public static final String ERROR_CODE_TENANT_MISMATCH = GatewayErrorCodes.TENANT_MISMATCH;
    public static final String CLAIM_TENANT_ID = "tenant_id";
    /** SUPER_ADMIN platform-scope wildcard. */
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
            // Platform-scope SUPER_ADMIN — pass through.
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
