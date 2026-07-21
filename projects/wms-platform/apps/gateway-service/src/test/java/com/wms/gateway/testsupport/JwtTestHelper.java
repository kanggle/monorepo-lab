package com.wms.gateway.testsupport;

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
 * Local RSA-backed JWT test helper. Used by both the no-Docker self-test in
 * the {@code test} source set and by the Testcontainers e2e suite.
 *
 * <p>Generates a 2048-bit RSA keypair on construction, exposes the public
 * half as a JWKS JSON document (served by MockWebServer at
 * {@code /.well-known/jwks.json}), and signs JWTs with the private half.
 * The gateway's {@code JWT_JWKS_URI} env var points at the MockWebServer so
 * Spring Security's oauth2 resource-server can validate signatures against
 * the same key.
 */
public final class JwtTestHelper {

    /**
     * Default issuer matches the legacy {@code POST /api/auth/login} issuer
     * ({@code "iam"}) — kept on the
     * {@link com.example.security.oauth2.AllowedIssuersValidator} allowlist while
     * D2-b deprecation is in flight (TASK-MONO-019).
     */
    public static final String LEGACY_ISSUER = "iam";
    /** Issuer URL used by SAS-issued tokens (TASK-MONO-019). */
    public static final String SAS_ISSUER = "http://localhost:8081";
    /** Required tenant for the WMS gateway (TASK-MONO-019). */
    public static final String DEFAULT_TENANT_ID = "wms";

    private final RSAKey rsaJwk;
    private final RSASSASigner signer;

    /**
     * A SECOND, independently-generated RSA keypair whose public half is deliberately
     * <strong>never</strong> published in {@link #jwksJson()}. Used by
     * {@link #signForgedSignatureToken(String)} to produce a token whose signature cannot
     * validate against any published key — a deterministic "tampered signature" that does not
     * depend on mangling bytes of a real signature.
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
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to build RSA signer", e);
        }
        try {
            RSAKey foreignJwk = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            this.foreignSigner = new RSASSASigner(foreignJwk);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to build foreign (unpublished) RSA signer", e);
        }
    }

    /** JWKS JSON document (public key only). Safe to publish via MockWebServer. */
    public String jwksJson() {
        return new JWKSet(rsaJwk.toPublicJWK()).toString();
    }

    /** Builds and signs a token with the given subject, role, and TTL. */
    public String signToken(String subject, String role, long ttlSeconds) {
        return signToken(subject, role, ttlSeconds, Map.of());
    }

    /**
     * Builds and signs a token. The {@code additionalClaims} map is merged
     * on top of the standard claims; callers typically add {@code email} or
     * a {@code roles} array.
     */
    public String signToken(String subject, String role, long ttlSeconds,
                            Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(LEGACY_ISSUER)
                .claim("tenant_id", DEFAULT_TENANT_ID)
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
     * Builds a structurally valid wms operator token (correct issuer/tenant/role) that
     * <strong>advertises the real published {@code kid}</strong> in its JWS header — so the
     * resource server selects the published key to verify — but is <strong>signed with the
     * foreign, unpublished private key</strong>. The signature therefore never matches the
     * published public key, yielding a <em>deterministic</em> 401.
     *
     * <p>Why this instead of flipping a byte of a real signature: an RSA-2048 signature is
     * 256 bytes → 342 base64url chars, and the <em>last</em> char encodes only 2 significant
     * bits + 4 padding bits (its value ∈ {@code {A,Q,g,w}}). Flipping the last char when it is
     * {@code 'A'} (low 2 bits = 00) toggles only a padding bit, so the decoded signature bytes
     * are unchanged, the "tampered" token still verifies, and the test flakes ~25% of runs
     * depending on the generated key. Forging with a foreign key removes that dependence
     * entirely — do not reintroduce the byte-flip.
     */
    public String signForgedSignatureToken(String subject) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(LEGACY_ISSUER)
                .claim("tenant_id", DEFAULT_TENANT_ID)
                .claim("role", "MASTER_READ")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        // Advertise the REAL published kid so the decoder selects the published key…
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaJwk.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            // …but sign with the foreign key the JWKS never published → signature mismatch.
            jwt.sign(foreignSigner);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign forged-signature JWT", e);
        }
        return jwt.serialize();
    }

    /**
     * Builds and signs an OPERATOR token with {@code aud: wms} and
     * {@code account_type: OPERATOR} for WMS E2E testing. Valid for 5 minutes.
     */
    public String signWmsOperatorToken(String subject, String primaryRole, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(LEGACY_ISSUER)
                .claim("tenant_id", DEFAULT_TENANT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .audience(List.of("wms"))
                .claim("account_type", "OPERATOR")
                .claim("email", subject + "@test.local");
        if (primaryRole != null) {
            claims.claim("role", primaryRole);
        }
        if (roles != null && !roles.isEmpty()) {
            claims.claim("roles", roles);
        }

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
     * Convenience for tests that need a token carrying the canonical WMS
     * master roles. Returns a token valid for 5 minutes with {@code aud: wms}
     * and {@code account_type: OPERATOR}.
     */
    public String signMasterWriteToken(String subject) {
        return signWmsOperatorToken(subject, "MASTER_WRITE", List.of("MASTER_WRITE", "MASTER_READ"));
    }

    public String signMasterReadToken(String subject) {
        return signWmsOperatorToken(subject, "MASTER_READ", List.of("MASTER_READ"));
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
