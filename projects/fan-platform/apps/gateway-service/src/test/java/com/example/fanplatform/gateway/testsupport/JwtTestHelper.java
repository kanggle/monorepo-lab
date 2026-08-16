package com.example.fanplatform.gateway.testsupport;

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
 * Local RSA-backed JWT test helper for the fan-platform gateway. Used by both
 * the no-Docker validator self-tests and by the Testcontainers integration suite.
 *
 * <p>Generates a 2048-bit RSA keypair on construction, exposes the public half
 * as a JWKS JSON document (served by MockWebServer at
 * {@code /oauth2/jwks}), and signs JWTs with the private half. The
 * gateway's {@code JWT_JWKS_URI} env var points at the MockWebServer so Spring
 * Security's oauth2 resource-server validates signatures against the same key.
 */
public final class JwtTestHelper {

    /** Legacy issuer string kept on the AllowedIssuersValidator allowlist. */
    public static final String LEGACY_ISSUER = "iam";
    /** Issuer URL used by SAS-issued tokens (matches application.yml default). */
    public static final String SAS_ISSUER = "http://iam.local";
    /** Required tenant for the fan-platform gateway. */
    public static final String DEFAULT_TENANT_ID = "fan-platform";

    private final RSAKey rsaJwk;
    private final RSASSASigner signer;
    /**
     * A second keypair, deliberately never published in the JWKS, used by
     * {@link #signForgedSignatureToken}. Carries the same {@code kid} as the real key so
     * the resource server still selects a key and the rejection is genuinely a signature
     * failure rather than "no key found".
     */
    private final RSASSASigner foreignSigner;

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
            RSAKey foreignJwk = new RSAKeyGenerator(2048).keyID(rsaJwk.getKeyID()).generate();
            this.foreignSigner = new RSASSASigner(foreignJwk);
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
     * Convenience: 5-minute valid fan-platform FAN token.
     */
    public String signFanToken(String subject) {
        return signToken(subject, "FAN", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("FAN"), "email", subject + "@test.local"));
    }

    /**
     * Convenience: 5-minute valid SUPER_ADMIN platform-scope token (tenant_id="*").
     */
    public String signSuperAdminToken(String subject) {
        return signToken(subject, "SUPER_ADMIN", "*", 300,
                Map.of("roles", List.of("SUPER_ADMIN")));
    }

    /**
     * Convenience: token whose tenant_id is wrong for fan-platform — used to
     * verify cross-tenant 403 behaviour.
     */
    public String signCrossTenantToken(String subject) {
        return signToken(subject, "OPERATOR", "wms", 300, Map.of());
    }

    /**
     * Convenience: a valid fan-platform token (correct tenant, issuer, signature) carrying
     * neither a role nor a scope — authenticated but unauthorized. Rule-6 admission
     * (TASK-MONO-416) must reject it with 403 {@code FORBIDDEN}.
     */
    public String signNoRoleToken(String subject) {
        return signToken(subject, null, DEFAULT_TENANT_ID, 300,
                Map.of("email", subject + "@test.local"));
    }

    /**
     * A fan-platform token whose claims are all valid but whose signature is produced by a
     * foreign key NOT in the JWKS (while advertising the real {@code kid}), so verification
     * ALWAYS fails → 401.
     *
     * <p>Replaces a byte-flip tamper — the last straggler of the sweep that fixed erp and wms
     * in TASK-MONO-458 and finance in MONO-461. scm was closed by TASK-MONO-542, which is also
     * where this one was found: fan reddened MONO-542's own PR run on a change that touched
     * nothing in fan.
     *
     * <p>Why the byte-flip was wrong: flipping the LAST base64url character of an RSA-2048
     * signature only touches padding bits about a quarter of the time. 256 bytes is 2048 bits,
     * base64url packs 6 bits per character, and 2048 = 6 × 341 + 2 — so the 342nd character
     * carries 2 significant bits plus 4 padding bits, and {@code 'A'} ({@code 000000}) and
     * {@code 'B'} ({@code 000001}) are identical in the two that count.
     *
     * <p>Measured here on fan's own keys over 400 freshly signed tokens (TASK-MONO-543 AC-1,
     * deliberately not inherited from scm's run): the final character is always one of
     * {@code A Q g w}, and the mutation left the decoded signature <strong>byte-identical 104
     * times — 26.0%</strong>, every one of them an {@code 'A'}. The "tampered" token then
     * verified, the gateway correctly routed it downstream, the shared MockWebServer had
     * nothing queued and blocked, and the test died five seconds later on {@code Timeout on
     * blocking read}. Signing with a foreign key never verifies, for any key, on any run.
     */
    public String signForgedSignatureToken(String subject) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(SAS_ISSUER)
                .claim("tenant_id", DEFAULT_TENANT_ID)
                .claim("role", "FAN")
                .claim("roles", List.of("FAN"))
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(foreignSigner);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT with the foreign key", e);
        }
        return jwt.serialize();
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
