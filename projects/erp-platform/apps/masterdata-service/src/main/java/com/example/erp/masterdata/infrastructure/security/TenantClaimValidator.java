package com.example.erp.masterdata.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service-level fail-closed re-validation of {@code tenant_id} (architecture.md
 * § Multi-tenancy). Even if a request bypasses the gateway, this
 * validator rejects cross-tenant traffic at JWT decode time.
 *
 * <p>The gate is <strong>entitlement-trust dual-accept</strong>
 * (ADR-MONO-019 § D5, dual-accept window). A token is accepted when either:
 * <ul>
 *   <li>(legacy) {@code tenant_id ∈ {expectedTenantId, "*"}} — {@code "*"}
 *       wildcard is the SUPER_ADMIN platform-scope; or</li>
 *   <li>(entitlement-trust) the GAP-signed {@code entitled_domains} claim
 *       (a list of domain keys) contains {@code expectedTenantId}.</li>
 * </ul>
 * Rejection requires <strong>both</strong> legacy and entitlement to fail
 * (fail-closed). {@code entitled_domains} is read only from an RS256/JWKS-
 * verified token, so it is unforgeable; while GAP has not yet populated it the
 * claim is absent and only the legacy path applies (production net-zero).
 * Row-level isolation downstream still keys off {@code tenant_id}.
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    public static final String ERROR_CODE_TENANT_MISMATCH = "tenant_mismatch";
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";
    public static final String WILDCARD_TENANT = "*";

    private final String expectedTenantId;

    public TenantClaimValidator(String expectedTenantId) {
        this.expectedTenantId = Objects.requireNonNull(expectedTenantId, "expectedTenantId");
    }

    /**
     * Single source of truth for the entitlement-trust branch shared by the
     * decode-time validator and the presentation filter. Returns {@code true}
     * iff the verified {@code entitled_domains} claim is a non-empty list of
     * strings that contains {@code domain}. Any claim shape anomaly
     * (absent / non-list / null or non-string element) yields {@code false}
     * (fail-closed — no NPE, no blanket trust).
     */
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
        // getClaimAsStringList would throw on a non-string element; iterate
        // defensively so a malformed claim degrades to "not entitled".
        ArrayList<String> result = new ArrayList<>(list.size());
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
                && (WILDCARD_TENANT.equals(tenantId) || expectedTenantId.equals(tenantId));
        if (legacyOk) {
            return OAuth2TokenValidatorResult.success();
        }
        // Entitlement-trust dual-accept: the signed entitled_domains claim may
        // grant access even when tenant_id does not match the legacy slug.
        if (isEntitled(jwt, expectedTenantId)) {
            return OAuth2TokenValidatorResult.success();
        }
        if (tenantId == null || tenantId.isBlank()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE_TENANT_MISMATCH, "tenant_id claim is required", null));
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                ERROR_CODE_TENANT_MISMATCH,
                "tenant_id '" + tenantId + "' is not allowed", null));
    }
}
