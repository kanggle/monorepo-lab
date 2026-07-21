package com.example.erp.gateway.testsupport;

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
 * RSA-backed JWT test helper for the erp-platform gateway (TASK-MONO-458).
 *
 * <p>Generates a 2048-bit RSA keypair on construction, exposes the public half as a JWKS JSON
 * document (served by {@link JwksMockServer} at {@code /oauth2/jwks}), and signs JWTs with the
 * private half. The gateway's resource-server fetches the JWKS and verifies signatures against
 * the same key. Modelled on scm's helper, adapted to erp's tenant ({@code erp}) and claim shape.
 */
public final class JwtTestHelper {

    /** Legacy issuer string kept on erp's {@code erpplatform.oauth2.allowed-issuers} allowlist. */
    public static final String LEGACY_ISSUER = "iam";
    /** Issuer URL used by SAS-issued tokens (matches application.yml default). */
    public static final String SAS_ISSUER = "http://iam.local";
    /** An issuer that is NOT on erp's allowlist — used to prove the issuer check rejects. */
    public static final String UNTRUSTED_ISSUER = "http://evil.example.com";
    /** Required tenant for the erp-platform gateway. */
    public static final String DEFAULT_TENANT_ID = "erp";

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

    /** JWKS JSON document (public key only). Safe to publish via MockWebServer. */
    public String jwksJson() {
        return new JWKSet(rsaJwk.toPublicJWK()).toString();
    }

    /** Builds and signs a token with the given issuer, subject, role, tenant, and TTL. */
    public String signToken(String issuer, String subject, String role, String tenantId,
                            long ttlSeconds, Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
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

    /** Convenience: 5-minute valid erp operator token (human user shape). */
    public String signErpOperatorToken(String subject) {
        return signToken(SAS_ISSUER, subject, "ERP_OPERATOR", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("ERP_OPERATOR"), "email", subject + "@test.local"));
    }

    /**
     * Convenience: 5-minute valid client_credentials token — {@code scope} present, no role.
     * erp authorizes services on scope ({@code erp.read}/{@code erp.write}); this is the machine
     * caller shape rule-6 admission must admit (contrast {@link #signNoRoleNoScopeToken}).
     */
    public String signClientCredentialsToken(String clientId) {
        return signToken(SAS_ISSUER, clientId, null, DEFAULT_TENANT_ID, 300,
                Map.of("azp", clientId, "scope", "erp.read erp.write"));
    }

    /** Convenience: 5-minute valid SUPER_ADMIN platform-scope token ({@code tenant_id="*"}). */
    public String signSuperAdminToken(String subject) {
        return signToken(SAS_ISSUER, subject, "SUPER_ADMIN", "*", 300,
                Map.of("roles", List.of("SUPER_ADMIN")));
    }

    /**
     * Convenience: signature/issuer-valid token whose tenant is wrong for erp and which carries
     * no entitlement — the tenant gate must 403 it as {@code TENANT_FORBIDDEN}. Uses {@code wms}
     * (a real monorepo tenant) for realism.
     */
    public String signCrossTenantToken(String subject) {
        return signToken(SAS_ISSUER, subject, "OPERATOR", "wms", 300, Map.of());
    }

    /**
     * Convenience: valid erp token (correct tenant, issuer, signature) carrying neither a role
     * nor a scope — authenticated but NOT authorized. Rule-6 admission must reject it 403
     * {@code FORBIDDEN} (not {@code TENANT_FORBIDDEN}: it is the admission gate, not the tenant gate).
     */
    public String signNoRoleNoScopeToken(String subject) {
        return signToken(SAS_ISSUER, subject, null, DEFAULT_TENANT_ID, 300,
                Map.of("email", subject + "@test.local"));
    }

    /** Convenience: an already-expired erp operator token (negative issued/expiry window). */
    public String signExpiredToken(String subject) {
        Instant past = Instant.now().minusSeconds(600);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(SAS_ISSUER)
                .claim("tenant_id", DEFAULT_TENANT_ID)
                .claim("role", "ERP_OPERATOR")
                .issueTime(Date.from(past))
                .expirationTime(Date.from(past.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return jwt.serialize();
    }

    /** Convenience: valid signature/tenant but an issuer NOT on erp's allowlist → 401. */
    public String signUntrustedIssuerToken(String subject) {
        return signToken(UNTRUSTED_ISSUER, subject, "ERP_OPERATOR", DEFAULT_TENANT_ID, 300,
                Map.of("roles", List.of("ERP_OPERATOR")));
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }
}
