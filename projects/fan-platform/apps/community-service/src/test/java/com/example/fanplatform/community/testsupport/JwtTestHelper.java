package com.example.fanplatform.community.testsupport;

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
 * Local RSA-backed JWT signer for community-service tests. Mirrors the
 * gateway-service helper so behaviour is consistent across services.
 */
public final class JwtTestHelper {

    public static final String LEGACY_ISSUER = "global-account-platform";
    public static final String SAS_ISSUER = "http://test-issuer";
    public static final String DEFAULT_TENANT_ID = "fan-platform";

    private final RSAKey rsaJwk;
    private final RSASSASigner signer;

    public JwtTestHelper() {
        try {
            this.rsaJwk = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            this.signer = new RSASSASigner(rsaJwk);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate RSA test keypair", e);
        }
    }

    public String jwksJson() {
        return new JWKSet(rsaJwk.toPublicJWK()).toString();
    }

    public String signFanToken(String subject) {
        return sign(subject, "FAN", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("FAN"), "email", subject + "@test.local"));
    }

    public String signArtistToken(String subject) {
        return sign(subject, "ARTIST", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("ARTIST")));
    }

    public String signCrossTenantToken(String subject) {
        return sign(subject, "OPERATOR", "wms", 300, Map.of());
    }

    public String sign(String subject, String role, String tenantId, long ttlSeconds,
                       Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(SAS_ISSUER)
                .claim("tenant_id", tenantId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .jwtID(UUID.randomUUID().toString());
        if (role != null) claims.claim("role", role);
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

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
