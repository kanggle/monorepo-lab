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
                .issuer("https://test.local/issuer")
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
     * Builds and signs an OPERATOR token with {@code aud: wms} and
     * {@code account_type: OPERATOR} for WMS E2E testing. Valid for 5 minutes.
     */
    public String signWmsOperatorToken(String subject, String primaryRole, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://test.local/issuer")
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
