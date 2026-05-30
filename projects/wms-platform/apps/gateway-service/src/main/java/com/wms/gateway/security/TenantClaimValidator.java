package com.wms.gateway.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

/**
 * Rejects access tokens whose {@code tenant_id} claim does not match the expected
 * tenant for this gateway.
 *
 * <p>TASK-MONO-019: gateway-service is the {@code wms} edge. Tokens issued for any
 * other tenant ({@code fan-platform}, future {@code erp}/{@code scm}/...) MUST be
 * rejected at the edge so cross-tenant tokens never reach internal services. The
 * validator raises a granular error code so the {@link
 * org.springframework.security.web.server.ServerAuthenticationEntryPoint} can map
 * cross-tenant misuse to 403 ({@code TENANT_FORBIDDEN}).
 *
 * <p>The gate is <strong>entitlement-trust dual-accept</strong> (ADR-MONO-019 § D5):
 * a token also passes when the GAP-signed {@code entitled_domains} claim contains the
 * expected tenant, even if {@code tenant_id} does not match. wms keeps strict legacy
 * equality (no {@code "*"} wildcard); rejection requires both legacy and entitlement
 * to fail. While GAP has not populated the claim it is absent → legacy-only (net-zero).
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    public static final String ERROR_CODE_TENANT_MISMATCH = "tenant_mismatch";
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";

    private final String expectedTenantId;

    public TenantClaimValidator(String expectedTenantId) {
        this.expectedTenantId = Objects.requireNonNull(expectedTenantId, "expectedTenantId");
    }

    public static boolean isEntitled(Jwt jwt, String domain) {
        if (jwt == null || domain == null) {
            return false;
        }
        List<String> entitled = safeStringList(jwt);
        return entitled.contains(domain);
    }

    private static List<String> safeStringList(Jwt jwt) {
        Object raw = jwt.getClaims().get(CLAIM_ENTITLED_DOMAINS);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof String s) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object raw = jwt.getClaim(CLAIM_TENANT_ID);
        String tenantId = raw instanceof String s ? s : null;
        boolean legacyOk = tenantId != null && !tenantId.isBlank()
                && expectedTenantId.equals(tenantId);
        if (legacyOk) {
            return OAuth2TokenValidatorResult.success();
        }
        // Entitlement-trust dual-accept (ADR-MONO-019 § D5): the signed
        // entitled_domains claim may grant access even when tenant_id does not
        // match the legacy slug. wms keeps strict legacy equality (no "*" wildcard).
        if (isEntitled(jwt, expectedTenantId)) {
            return OAuth2TokenValidatorResult.success();
        }
        if (tenantId == null || tenantId.isBlank()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE_TENANT_MISMATCH,
                    "tenant_id claim is required",
                    null));
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                ERROR_CODE_TENANT_MISMATCH,
                "tenant_id '" + tenantId + "' is not allowed",
                null));
    }
}
