package com.example.finance.gateway.testsupport;

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
 * Local RSA-backed JWT signer for the finance-platform gateway integration suite.
 *
 * <p>finance had <em>no</em> JWT test infrastructure before TASK-MONO-458 — its unit tests
 * lean on the shared {@code GatewayTestJwts} fixture, which cannot mint a JWKS-verifiable
 * RS256 token an over-the-wire resource server will accept. This helper fills that gap,
 * modelled on scm's {@code JwtTestHelper} and adapted to finance's claims:
 * {@code tenant_id=finance}, issuer {@code http://iam.local} (the SAS issuer on finance's
 * allowlist), and finance's rule-6 operator roles.
 *
 * <p>Generates a 2048-bit keypair on construction, publishes the public half as a JWKS
 * document (served by {@link JwksMockServer}) and signs tokens with the private half.
 */
public final class JwtTestHelper {

    /** Legacy issuer string kept on finance's AllowedIssuersValidator allowlist. */
    public static final String LEGACY_ISSUER = "iam";
    /** SAS issuer URL — finance's {@code application.yml} default and allowlist head. */
    public static final String SAS_ISSUER = "http://iam.local";
    /** Required tenant for the finance-platform gateway ({@code required-tenant-id}). */
    public static final String DEFAULT_TENANT_ID = "finance";

    private final RSAKey rsaJwk;
    private final RSASSASigner signer;

    public JwtTestHelper() {
        try {
            this.rsaJwk = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            this.signer = new RSASSASigner(rsaJwk);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to build RSA test keypair/signer", e);
        }
    }

    /** JWKS JSON document (public key only). Safe to publish via {@link JwksMockServer}. */
    public String jwksJson() {
        return new JWKSet(rsaJwk.toPublicJWK()).toString();
    }

    /** Full control: issuer, subject, tenant, TTL and any additional claims. */
    public String signToken(String issuer, String subject, String tenantId, long ttlSeconds,
                            Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .claim("tenant_id", tenantId)
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .jwtID(UUID.randomUUID().toString());
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

    /** Convenience: 5-minute valid finance OPERATOR token (human user shape). */
    public String signFinanceToken(String subject) {
        return signToken(SAS_ISSUER, subject, DEFAULT_TENANT_ID, 300,
                Map.of("role", "OPERATOR",
                        "roles", List.of("OPERATOR"),
                        "email", subject + "@test.local"));
    }

    /**
     * Convenience: 5-minute machine token carrying a scope but no role. Rule-6 admission
     * ({@code RoleAdmissions.roleOrScope}) admits it on the scope leg — the regression guard
     * that admission gates on "role OR scope", not role alone.
     */
    public String signScopeOnlyToken(String subject) {
        return signToken(SAS_ISSUER, subject, DEFAULT_TENANT_ID, 300,
                Map.of("scope", "finance.read"));
    }

    /** Convenience: 5-minute SUPER_ADMIN platform-scope token ({@code tenant_id="*"}). */
    public String signSuperAdminToken(String subject) {
        return signToken(SAS_ISSUER, subject, "*", 300,
                Map.of("role", "SUPER_ADMIN", "roles", List.of("SUPER_ADMIN")));
    }

    /**
     * Convenience: token whose {@code tenant_id} is wrong for finance (uses {@code wms}, a
     * known monorepo tenant) and carries no entitlement — the tenant gate must 403 it with
     * {@code TENANT_FORBIDDEN}.
     */
    public String signCrossTenantToken(String subject) {
        return signToken(SAS_ISSUER, subject, "wms", 300,
                Map.of("role", "OPERATOR"));
    }

    /**
     * Convenience: a signature/tenant/issuer-valid finance token carrying neither a role nor a
     * scope — authenticated but unauthorized. Rule-6 admission must 403 it with {@code FORBIDDEN}
     * (distinct from the tenant gate's {@code TENANT_FORBIDDEN}).
     */
    public String signNoRoleToken(String subject) {
        return signToken(SAS_ISSUER, subject, DEFAULT_TENANT_ID, 300,
                Map.of("email", subject + "@test.local"));
    }

    /** Convenience: a well-formed finance token that expired 60s ago. */
    public String signExpiredToken(String subject) {
        return signToken(SAS_ISSUER, subject, DEFAULT_TENANT_ID, -60,
                Map.of("role", "OPERATOR"));
    }

    /**
     * Convenience: a finance token signed with an issuer that is NOT on the allowlist. Its
     * signature and tenant are fine, but AllowedIssuersValidator rejects it → 401.
     */
    public String signWrongIssuerToken(String subject) {
        return signToken("http://evil.example", subject, DEFAULT_TENANT_ID, 300,
                Map.of("role", "OPERATOR"));
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
