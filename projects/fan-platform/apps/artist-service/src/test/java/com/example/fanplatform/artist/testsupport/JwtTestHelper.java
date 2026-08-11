package com.example.fanplatform.artist.testsupport;

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
 * Local RSA-backed JWT signer for artist-service tests. Mirrors the
 * community-service helper so behaviour is consistent across services.
 */
public final class JwtTestHelper {

    public static final String LEGACY_ISSUER = "iam";
    public static final String SAS_ISSUER = "http://test-issuer";
    public static final String DEFAULT_TENANT_ID = "fan-platform";

    /**
     * An issuer deliberately absent from every allow-list this service is configured with. A token
     * minted with it is well-formed in every other respect (right key, right tenant, unexpired), so
     * rejecting it can only come from the {@code AllowedIssuersValidator} arm of the decoder's
     * validator chain.
     */
    public static final String FOREIGN_ISSUER = "http://not-our-issuer";

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

    public String signAdminToken(String subject) {
        return sign(subject, "ADMIN", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("ADMIN")));
    }

    public String signOperatorToken(String subject) {
        return sign(subject, "OPERATOR", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("OPERATOR")));
    }

    /**
     * Assume-tenant operator token — iam's token-exchange mints the domain-prefixed
     * {@code FAN_OPERATOR} (OperatorRoleDerivation), never a generic role. Must be admitted on
     * admin-tier routes just like a directly-provisioned operator (TASK-MONO-417).
     */
    public String signFanOperatorToken(String subject) {
        return sign(subject, "FAN_OPERATOR", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("FAN_OPERATOR")));
    }

    public String signCrossTenantToken(String subject) {
        return sign(subject, "OPERATOR", "wms", 300, Map.of());
    }

    /**
     * Workload-identity client_credentials token, faithful to what the real IAM
     * mints for the artist-service internal client: {@code sub == aud == client_id},
     * {@code scope=["artist.read"]} (JSON array, the SAS shape), and — like every
     * IAM grant — a {@code tenant_id} claim (the {@code TenantClaimTokenCustomizer}
     * stamps it fail-closed; jwt-standard-claims.md). The receiver
     * ({@link com.example.fanplatform.artist.config.WorkloadIdentityAuthoritiesConverter})
     * recognizes it by the {@code artist.read} scope, NOT by tenant_id absence
     * (TASK-FAN-BE-029). No {@code client_id}/{@code azp} claim — the real cc token
     * has none.
     */
    public String signWorkloadToken(String clientId) {
        return signWorkloadTokenWithScope(clientId, "artist.read");
    }

    /**
     * A workload-shaped token carrying the END-USER resource scope
     * {@code fan-platform.artist.read} instead of the machine scope
     * {@code artist.read}. {@code WorkloadIdentityAuthoritiesConverter
     * .REQUIRED_WORKLOAD_SCOPE} discriminates on the exact scope string; a token
     * carrying this scope instead is what IAM migration {@code V0030} grants the
     * fan web client and what the demo seed requests on an ordinary user token —
     * it must NOT clear {@code ROLE_INTERNAL}.
     */
    public String signFanResourceScopedToken(String clientId) {
        return signWorkloadTokenWithScope(clientId, "fan-platform.artist.read");
    }

    private String signWorkloadTokenWithScope(String clientId, String scope) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(clientId)
                .audience(clientId)
                .issuer(SAS_ISSUER)
                .claim("tenant_id", DEFAULT_TENANT_ID)
                .claim("tenant_type", "B2C")
                .claim("scope", List.of(scope))
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

    /**
     * A token from {@link #FOREIGN_ISSUER} — same key, same tenant, unexpired. Only the issuer
     * allow-list stands between it and the controller.
     */
    public String signForeignIssuer(String subject) {
        return sign(FOREIGN_ISSUER, subject, null, DEFAULT_TENANT_ID, 300, Map.of());
    }

    public String sign(String subject, String role, String tenantId, long ttlSeconds,
                       Map<String, Object> additionalClaims) {
        return sign(SAS_ISSUER, subject, role, tenantId, ttlSeconds, additionalClaims);
    }

    private String sign(String issuer, String subject, String role, String tenantId, long ttlSeconds,
                        Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
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
