package com.example.fanplatform.e2e.testsupport;

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
 * Local RSA-backed JWT test helper for the fan-platform e2e suite. Adapted
 * from {@code projects/fan-platform/apps/gateway-service/src/test/.../JwtTestHelper}
 * — kept self-contained inside the e2e module because Gradle does not expose
 * one project's test-source classes to another project (and we deliberately
 * avoid promoting auth-related test helpers into the production module's main
 * classpath).
 *
 * <p>Generates a 2048-bit RSA keypair on construction, exposes the public half
 * as a JWKS JSON document (served by {@link JwksMockServer}), and signs JWTs
 * with the private half. Each fan-platform service container's
 * {@code JWT_JWKS_URI} env var points at the host JVM's JWKS server so Spring
 * Security's oauth2 resource-server validates signatures against the same key.
 *
 * <p>Tokens are issued with {@code iss=http://gap.local} (matches the SAS
 * issuer the gateway/community/artist services accept by default) and
 * {@code tenant_id=fan-platform} (matches the required tenant). Cross-tenant
 * tokens use {@code tenant_id=wms} to verify the gateway's tenant gate.
 */
public final class JwtTestHelper {

    /** Issuer URL used by SAS-issued tokens (matches application.yml default across all 3 services). */
    public static final String SAS_ISSUER = "http://gap.local";
    /** Required tenant for the fan-platform gateway. */
    public static final String DEFAULT_TENANT_ID = "fan-platform";
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

    /** Convenience: 10-minute valid fan-platform FAN token. */
    public String signFanToken(String subject) {
        return signToken(subject, "FAN", DEFAULT_TENANT_ID, DEFAULT_TTL_SECONDS,
                Map.of("roles", List.of("FAN"), "email", subject + "@test.local"));
    }

    /** Convenience: 10-minute valid fan-platform ADMIN token (artist-service mutating endpoints). */
    public String signAdminToken(String subject) {
        return signToken(subject, "ADMIN", DEFAULT_TENANT_ID, DEFAULT_TTL_SECONDS,
                Map.of("roles", List.of("ADMIN"), "email", subject + "@test.local"));
    }

    /** Convenience: 10-minute valid fan-platform OPERATOR token. */
    public String signOperatorToken(String subject) {
        return signToken(subject, "OPERATOR", DEFAULT_TENANT_ID, DEFAULT_TTL_SECONDS,
                Map.of("roles", List.of("OPERATOR")));
    }

    /** Convenience: SUPER_ADMIN platform-scope token (tenant_id="*"). */
    public String signSuperAdminToken(String subject) {
        return signToken(subject, "SUPER_ADMIN", "*", DEFAULT_TTL_SECONDS,
                Map.of("roles", List.of("SUPER_ADMIN")));
    }

    /**
     * Token whose tenant_id is wrong for fan-platform — used to verify the
     * gateway's cross-tenant 403 TENANT_FORBIDDEN behaviour.
     */
    public String signCrossTenantToken(String subject) {
        return signToken(subject, "OPERATOR", "wms", DEFAULT_TTL_SECONDS, Map.of());
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
