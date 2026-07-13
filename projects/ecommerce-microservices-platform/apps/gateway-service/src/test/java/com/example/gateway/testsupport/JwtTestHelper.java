package com.example.gateway.testsupport;

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
 * Local RSA-backed JWT test helper for the ecommerce gateway.
 *
 * <p>Generates a 2048-bit RSA keypair on construction, exposes the public
 * half as a JWKS JSON document (served by {@link JwksMockServer}), and signs
 * JWTs with the private half. Spring Security's oauth2 resource-server validates
 * signatures against the same key via the JWKS URI.
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

    /** Key ID for the generated RSA key. */
    public String keyId() {
        return rsaJwk.getKeyID();
    }

    /**
     * Builds and signs a CONSUMER token with {@code aud: ecommerce},
     * {@code account_type: CONSUMER}, {@code iss: https://test.local/issuer}
     * and {@code tenant_id: ecommerce}. Valid for 5 minutes.
     *
     * <p>The {@code tenant_id} claim is required by
     * {@code TenantClaimValidator} (TASK-MONO-027); it has been baked into
     * the standard helper so existing tests continue to pass after the
     * validator was added.
     */
    public String signConsumerToken(String subject, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://test.local/issuer")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .audience(List.of("ecommerce"))
                .claim("account_type", "CONSUMER")
                .claim("tenant_id", "ecommerce")
                .claim("email", subject + "@test.local");
        if (roles != null && !roles.isEmpty()) {
            claims.claim("roles", roles);
        }
        return sign(claims.build());
    }

    /**
     * Builds and signs an OPERATOR token with {@code aud: ecommerce},
     * {@code account_type: OPERATOR}, {@code iss: https://test.local/issuer}
     * and {@code tenant_id: ecommerce}. Valid for 5 minutes.
     */
    public String signOperatorToken(String subject, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://test.local/issuer")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .audience(List.of("ecommerce"))
                .claim("account_type", "OPERATOR")
                .claim("tenant_id", "ecommerce")
                .claim("email", subject + "@test.local");
        if (roles != null && !roles.isEmpty()) {
            claims.claim("roles", roles);
        }
        return sign(claims.build());
    }

    /**
     * Compact token builder — no audience claim. Useful for negative tests
     * (wrong aud → 401) or unit tests that don't need account_type.
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
        return sign(claims.build());
    }

    /**
     * Builds and signs a CONSUMER token with explicit {@code iss} and
     * {@code tenant_id} claims so TASK-MONO-027 validators
     * (AllowedIssuersValidator + TenantClaimValidator) can be exercised
     * end-to-end. Always carries {@code aud: ecommerce} and
     * {@code account_type: CONSUMER}. Valid for 5 minutes.
     *
     * @param issuer    explicit {@code iss} claim value
     * @param tenantId  {@code tenant_id} claim, or {@code null} to omit
     */
    public String signTokenWithIssuerAndTenant(String issuer, String tenantId) {
        return signTokenWithIssuerTenantAndEntitlements(issuer, tenantId, null);
    }

    /**
     * As {@link #signTokenWithIssuerAndTenant(String, String)}, plus the GAP-signed
     * {@code entitled_domains} claim.
     *
     * <p><strong>This is what lets a token whose {@code tenant_id} names another tenant reach the
     * ecommerce edge</strong> (TASK-MONO-388). A customer-tenant operator running their own store
     * carries {@code tenant_id=<their tenant>} and {@code entitled_domains ∋ ecommerce} — that
     * pair is the marketplace (ADR-MONO-030 § D1-A), and it is the only shape in which such a
     * token exists in production, because IAM issues {@code entitled_domains} from
     * {@code tenant_domain_subscription}.
     *
     * <p>Before TASK-MONO-388 the gate admitted any well-formed {@code tenant_id}, so a fixture
     * could name a foreign tenant with no entitlement and still get in. <strong>No such token can
     * be issued</strong> — the fixture was only possible against the gate, never against IAM.
     *
     * @param entitledDomains {@code entitled_domains} claim, or {@code null} to omit
     */
    public String signTokenWithIssuerTenantAndEntitlements(
            String issuer, String tenantId, List<String> entitledDomains) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user-" + UUID.randomUUID())
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .audience(List.of("ecommerce"))
                .claim("account_type", "CONSUMER");
        if (tenantId != null) {
            claims.claim("tenant_id", tenantId);
        }
        if (entitledDomains != null) {
            claims.claim("entitled_domains", entitledDomains);
        }
        return sign(claims.build());
    }

    /**
     * Builds and signs a CONSUMER token carrying the {@code CUSTOMER} role and an explicit
     * {@code tenant_id} (issuer {@code https://test.local/issuer}, {@code aud: ecommerce}).
     * Unlike {@link #signTokenWithIssuerAndTenant(String, String)} this also sets the
     * {@code roles} claim, so the token passes {@code AccountTypeEnforcementFilter}'s
     * role-based admission (ADR-MONO-035 4b-2a — a storefront consumer must carry
     * {@code CUSTOMER}; the {@code account_type} OR-branch was removed). Without the role
     * claim every request 403s at admission before reaching the rate limiter. Used by the
     * cross-tenant rate-limit isolation IT, where each tenant needs a distinct
     * {@code tenant_id} bucket. Valid for 5 minutes.
     *
     * <p><strong>Carries {@code entitled_domains: [ecommerce]}</strong> (TASK-MONO-388). The
     * tenant gate no longer admits any well-formed {@code tenant_id}; a token naming a tenant
     * other than {@code ecommerce} reaches this edge only by being entitled to it. That is not a
     * concession to the gate — it is the shape the token really has: a user of tenant A's store
     * exists because tenant A subscribed to ecommerce, and IAM writes that subscription into the
     * claim. <strong>Without it the fixture describes a token IAM cannot issue</strong>, and a
     * green test on an impossible input proves nothing.
     *
     * @param tenantId {@code tenant_id} claim, or {@code null} to omit
     */
    public String signCustomerTokenForTenant(String tenantId) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user-" + UUID.randomUUID())
                .issuer("https://test.local/issuer")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .audience(List.of("ecommerce"))
                .claim("account_type", "CONSUMER")
                .claim("roles", List.of("CUSTOMER"))
                .claim("entitled_domains", List.of("ecommerce"));
        if (tenantId != null) {
            claims.claim("tenant_id", tenantId);
        }
        return sign(claims.build());
    }

    private String sign(JWTClaimsSet claimsSet) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaJwk.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, claimsSet);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return jwt.serialize();
    }
}
