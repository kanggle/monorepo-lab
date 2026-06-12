package com.example.gateway.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * Validates the {@code tenant_id} claim at the ecommerce gateway edge.
 *
 * <p><strong>Entitlement-trust gate</strong> (ADR-MONO-019 § D5 / ADR-MONO-030
 * § 2.4, Step 2). ecommerce is being promoted to a multi-tenant marketplace SaaS,
 * so the edge no longer pins {@code tenant_id} to the fixed {@code 'ecommerce'}
 * slug. Any <em>well-formed</em> (non-blank) {@code tenant_id} carried by a JWKS-
 * and issuer-verified token is accepted; only a blank/missing claim is rejected
 * ({@code tenant_mismatch} → 403 {@code TENANT_FORBIDDEN}).
 *
 * <p>The entitlement decision is made upstream, at <strong>IAM issuance time</strong>
 * (an ecommerce-scoped token is only minted when a subscription + operator
 * assignment exist). The gateway therefore trusts issuance and enforces only
 * <strong>row-level isolation</strong> downstream — two authorities, no overlap.
 * Tenant separation is guaranteed by the persistence-layer {@code WHERE tenant_id}
 * filter and proven by the M6 cross-tenant-leak integration test, not by this gate.
 *
 * <p><strong>Dual-accept window</strong> (ADR-019 § D6): the legacy fixed-slug
 * token ({@code tenant_id == expectedTenantId}) is the single-store identity that
 * the default-tenant backfill maps 1:1, so it is recognised explicitly and always
 * passes (net-zero). New per-tenant tokens carry an arbitrary {@code tenant_id} and
 * pass the entitlement-trust branch. The explicit legacy branch documents the
 * cleanup point once the migration completes.
 *
 * <p>The validator raises a granular error code so the
 * {@link org.springframework.security.web.server.ServerAuthenticationEntryPoint}
 * can map a missing tenant to 403 ({@code TENANT_FORBIDDEN}). It is stateless and
 * reactive-safe (used by Spring Cloud Gateway's {@code NimbusReactiveJwtDecoder}).
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    public static final String ERROR_CODE_TENANT_MISMATCH = "tenant_mismatch";
    public static final String CLAIM_TENANT_ID = "tenant_id";

    /** Legacy single-store slug, recognised explicitly during the dual-accept window. */
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
        // Dual-accept window (ADR-019 § D6): the legacy fixed-slug token is the
        // single-store identity mapped 1:1 by the default-tenant backfill (net-zero).
        if (expectedTenantId.equals(tenantId)) {
            return OAuth2TokenValidatorResult.success();
        }
        // Entitlement-trust (ADR-019 § D5 / ADR-030 § 2.4): the multi-tenant SaaS
        // edge accepts any well-formed tenant_id from a verified token; entitlement
        // was decided at IAM issuance, and ecommerce enforces only row isolation.
        return OAuth2TokenValidatorResult.success();
    }
}
