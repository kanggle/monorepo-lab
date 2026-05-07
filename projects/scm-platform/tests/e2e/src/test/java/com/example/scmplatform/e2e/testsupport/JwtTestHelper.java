package com.example.scmplatform.e2e.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local RSA-backed JWT test helper for the scm-platform e2e suite. Adapted
 * from {@code projects/fan-platform/tests/e2e/.../JwtTestHelper}, but tunes
 * issuer + tenant for the GAP -> scm-platform integration.
 *
 * <p>Generates a 2048-bit RSA keypair on construction, exposes the public half
 * as a JWKS JSON document (served by {@link JwksMockServer}), and signs JWTs
 * with the private half. Each scm-platform service container's
 * {@code JWT_JWKS_URI} env var points at the host JVM's JWKS server so Spring
 * Security's oauth2 resource-server validates signatures against the same key.
 *
 * <p>Tokens are issued with {@code iss=http://gap.local} (matches the SAS
 * issuer scm gateway/procurement/inventory-visibility services accept by
 * default — see each service's {@code allowed-issuers} in application.yml)
 * and {@code tenant_id=scm} (matches the required tenant). Cross-tenant
 * tokens use {@code tenant_id=wms} to verify the procurement service's
 * tenant-scoped queries reject foreign actors (Edge Case #5 of the task spec).
 */
public final class JwtTestHelper {

    /** Issuer URL used by SAS-issued tokens (matches application.yml default across all 3 services). */
    public static final String SAS_ISSUER = "http://gap.local";
    /** Required tenant for the scm-platform stack. */
    public static final String DEFAULT_TENANT_ID = "scm";
    /** Token lifetime — generous so a slow CI run never trips an exp boundary. */
    public static final long DEFAULT_TTL_SECONDS = 600;

    private final RSAKey rsaJwk;
    private final RSASSASigner signer;

    public JwtTestHelper() {
        try {
            this.rsaJwk = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate RSA test keypair", e);
        }
        try {
            this.signer = new RSASSASigner(rsaJwk);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to build RSA signer", e);
        }
    }

    /** JWKS JSON document (public key only). Safe to publish via MockWebServer. */
    public String jwksJson() {
        return new JWKSet(rsaJwk.toPublicJWK()).toString();
    }

    /** Builds and signs a token with the given subject, role, tenant_id, and TTL. */
    public String signToken(String subject, String role, String tenantId, long ttlSeconds,
                            Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(SAS_ISSUER)
                .claim("tenant_id", tenantId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .jwtID(UUID.randomUUID().toString());
        if (role != null) {
            claims.claim("role", role);
        }
        additionalClaims.forEach(claims::claim);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaJwk.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return jwt.serialize();
    }

    /** Convenience: 10-minute valid scm BUYER token (drafts + submits + confirms POs). */
    public String signBuyerToken(String subject) {
        return signToken(subject, "BUYER", DEFAULT_TENANT_ID, DEFAULT_TTL_SECONDS,
                Map.of("roles", List.of("BUYER"), "email", subject + "@test.local"));
    }

    /** Convenience: 10-minute valid scm OPERATOR token (PO confirm path). */
    public String signOperatorToken(String subject) {
        return signToken(subject, "OPERATOR", DEFAULT_TENANT_ID, DEFAULT_TTL_SECONDS,
                Map.of("roles", List.of("OPERATOR")));
    }

    /**
     * Token whose tenant_id is wrong for scm-platform — used to verify the
     * cross-tenant 403 / 404 behaviour (CrossTenantIsolationE2ETest, Edge
     * Case #5: scoping leaks).
     */
    public String signCrossTenantToken(String subject) {
        return signToken(subject, "BUYER", "wms", DEFAULT_TTL_SECONDS,
                Map.of("roles", List.of("BUYER")));
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
