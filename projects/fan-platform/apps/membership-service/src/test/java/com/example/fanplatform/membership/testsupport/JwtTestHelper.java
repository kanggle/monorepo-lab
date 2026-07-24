package com.example.fanplatform.membership.testsupport;

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
 * Local RSA-backed JWT signer for membership-service tests. Signs end-user
 * tokens (tenant-pinned, role/roles) AND workload-identity client_credentials
 * tokens (no tenant_id, scope + client_id markers) for the /internal/** chain.
 */
public final class JwtTestHelper {

    public static final String LEGACY_ISSUER = "iam";
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
        return signEndUser(subject, DEFAULT_TENANT_ID,
                Map.of("roles", List.of("FAN"), "email", subject + "@test.local"));
    }

    public String signCrossTenantToken(String subject) {
        return signEndUser(subject, "wms", Map.of("roles", List.of("OPERATOR")));
    }

    /** End-user token: tenant-pinned, carries a sub + tenant_id + roles. */
    public String signEndUser(String subject, String tenantId, Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(SAS_ISSUER)
                .claim("tenant_id", tenantId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString());
        additionalClaims.forEach(claims::claim);
        return sign(claims.build());
    }

    /**
     * Workload-identity client_credentials token, faithful to what the real IAM
     * mints for {@code community-service-client}: {@code sub == aud == client_id},
     * {@code scope=["membership.read"]} (JSON array, the SAS shape), and — like
     * EVERY IAM grant — a {@code tenant_id} claim (the {@code TenantClaimTokenCustomizer}
     * stamps it fail-closed; jwt-standard-claims.md). The receiver recognizes it
     * by the {@code membership.read} scope, NOT by tenant_id absence
     * (TASK-FAN-BE-029). No {@code client_id}/{@code azp} claim — the real cc token
     * has none.
     */
    public String signWorkloadToken(String clientId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(clientId)
                .audience(clientId)
                .issuer(SAS_ISSUER)
                .claim("tenant_id", DEFAULT_TENANT_ID)
                .claim("tenant_type", "B2C")
                .claim("scope", List.of("membership.read"))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        return sign(claims);
    }

    private String sign(JWTClaimsSet claims) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaJwk.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
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
