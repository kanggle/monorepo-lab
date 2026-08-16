package com.example.scmplatform.gateway.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Arrays;
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

    /**
     * Convenience: a valid scm token (correct tenant, issuer, signature) carrying neither a
     * role nor a scope — authenticated but unauthorized. Rule-6 admission (TASK-MONO-416)
     * must reject it with 403 {@code FORBIDDEN}. Contrast {@link #signClientCredentialsToken()},
     * which carries a scope and is therefore admitted.
     */
    public String signNoRoleToken(String subject) {
        return signToken(subject, null, DEFAULT_TENANT_ID, 300,
                Map.of("email", subject + "@test.local"));
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }

    /**
     * Returns {@code token} with its signature altered so that verification is
     * guaranteed to fail.
     *
     * <h2>🔴 Why this is a method and not three lines at the call site</h2>
     *
     * It used to be three lines at the call site, and they were wrong. They flipped the
     * <em>last</em> character of the signature ({@code 'A'} → {@code 'B'}, anything else
     * → {@code 'A'}), which for an RS256/2048 signature is the one character that mostly
     * does not matter: 256 bytes is 2048 bits, base64url packs 6 bits per character, and
     * 2048 = 6 × 341 + 2 — so the 342nd character carries only <strong>2 real bits</strong>
     * plus 4 bits of padding. {@code 'A'} is {@code 000000} and {@code 'B'} is
     * {@code 000001}: identical in the two bits that count. Whenever the signature ended
     * in {@code 'A'}, the "tampered" token decoded to <strong>byte-identical</strong>
     * signature bytes and stayed perfectly valid.
     *
     * <p>Measured over 400 freshly signed tokens: the last character is always one of
     * {@code A Q g w} (the four with four zero padding bits), and the mutation left the
     * signature bytes unchanged <strong>107 times — 26.75%</strong>, every one of them an
     * {@code 'A'}. So roughly a quarter of runs sent a <em>valid</em> token to a test
     * asserting 401. The gateway did the correct thing and routed it downstream, the
     * MockWebServer had nothing queued and blocked, and the test died five seconds later
     * on {@code Timeout on blocking read} — a failure that reads like a gateway or
     * infrastructure problem and is neither.
     *
     * <p>This mutates the <em>first</em> signature character instead, where all six bits
     * are real, and then verifies that the decoded bytes actually changed. A negative test
     * that cannot confirm its own premise is not a negative test, and the cost of finding
     * that out through an intermittent CI red is what this method exists to prevent.
     */
    public static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("not a signed JWT: " + token);
        }
        char[] signature = parts[2].toCharArray();
        signature[0] = (signature[0] == 'A') ? 'B' : 'A';
        String tampered = new String(signature);
        if (Arrays.equals(new Base64URL(parts[2]).decode(), new Base64URL(tampered).decode())) {
            throw new IllegalStateException(
                    "tamperSignature() left the signature bytes unchanged — the token would "
                            + "still verify, so the caller would be asserting on a valid token");
        }
        return parts[0] + "." + parts[1] + "." + tampered;
    }
}
