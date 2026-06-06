package com.example.scmplatform.gateway.testsupport;

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
 * Local RSA-backed JWT test helper for the scm-platform gateway. Used by both
 * the no-Docker validator self-tests and by the Testcontainers integration suite.
 *
 * <p>Generates a 2048-bit RSA keypair on construction, exposes the public half
 * as a JWKS JSON document (served by MockWebServer at {@code /oauth2/jwks}),
 * and signs JWTs with the private half. The gateway's {@code JWT_JWKS_URI} env
 * var points at the MockWebServer so Spring Security's oauth2 resource-server
 * validates signatures against the same key.
 */
public final class JwtTestHelper {

    /** Legacy issuer string kept on the AllowedIssuersValidator allowlist. */
    public static final String LEGACY_ISSUER = "iam";
    /** Issuer URL used by SAS-issued tokens (matches application.yml default). */
    public static final String SAS_ISSUER = "http://iam.local";
    /** Required tenant for the scm-platform gateway. */
    public static final String DEFAULT_TENANT_ID = "scm";
    /** V0013-seeded internal client_id (per TASK-MONO-042). */
    public static final String INTERNAL_CLIENT_ID = "scm-platform-internal-services-client";

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
    public String signToken(String subject, String role, String tenantId, long ttlSeconds) {
        return signToken(subject, role, tenantId, ttlSeconds, Map.of());
    }

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

    /**
     * Convenience: 5-minute valid scm BUYER token (human user shape).
     */
    public String signScmToken(String subject) {
        return signToken(subject, "BUYER", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("BUYER"), "email", subject + "@test.local"));
    }

    /**
     * Convenience: 5-minute valid client_credentials token. Mirrors the
     * V0013-seeded {@code scm-platform-internal-services-client} grant —
     * {@code sub = client_id}, {@code azp = client_id}, {@code scope = "scm.read scm.write"},
     * no email/roles claims (Edge Case E1 / E3). This is scm v1's primary
     * authentication shape since v1 is backend-only.
     */
    public String signClientCredentialsToken() {
        return signToken(INTERNAL_CLIENT_ID, null, DEFAULT_TENANT_ID, 300,
                Map.of(
                        "azp", INTERNAL_CLIENT_ID,
                        "aud", List.of(INTERNAL_CLIENT_ID),
                        "scope", "scm.read scm.write"));
    }

    /**
     * Convenience: 5-minute valid SUPER_ADMIN platform-scope token (tenant_id="*").
     */
    public String signSuperAdminToken(String subject) {
        return signToken(subject, "SUPER_ADMIN", "*", 300,
                Map.of("roles", List.of("SUPER_ADMIN")));
    }

    /**
     * Convenience: token whose tenant_id is wrong for scm-platform — used to
     * verify cross-tenant 403 behaviour. Uses {@code wms} (a known existing
     * tenant in the monorepo) for realism.
     */
    public String signCrossTenantToken(String subject) {
        return signToken(subject, "OPERATOR", "wms", 300, Map.of());
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
