package com.example.community.support;

import com.example.security.jwt.Rs256JwtSigner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test fixture used by community-service slice / unit tests (TASK-BE-253).
 *
 * <p>Replaces the previous fixture that wired the legacy {@code Rs256JwtVerifier}
 * directly. Now exposes:
 * <ul>
 *   <li>{@link #token(String, List)} — creates legacy-shape (iss=global-account-platform) signed JWTs</li>
 *   <li>{@link #tokenWithIssuer(String, String, List, String)} — overrides issuer/tenant for cross-tenant tests</li>
 *   <li>{@link #jwtDecoder()} — a {@link JwtDecoder} that verifies with this fixture's public key,
 *       wrapped with the same custom validators as the production {@code OAuth2ResourceServerConfig}.</li>
 * </ul>
 */
public final class AccountJwtTestFixture {

    public static final String LEGACY_ISSUER = "global-account-platform";
    public static final String SAS_ISSUER = "http://localhost:8081";
    public static final String DEFAULT_TENANT_ID = "fan-platform";

    private final Rs256JwtSigner signer;
    private final RSAPublicKey publicKey;

    public AccountJwtTestFixture() {
        KeyPair kp = generateKeyPair();
        this.signer = new Rs256JwtSigner(kp.getPrivate(), "test-key-001");
        this.publicKey = (RSAPublicKey) kp.getPublic();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Signs a token with the legacy issuer ({@code global-account-platform}) and
     * the default tenant {@code fan-platform}. Mirrors the runtime
     * {@code POST /api/auth/login} JWT shape.
     */
    public String token(String sub, List<String> roles) {
        return tokenWithIssuer(sub, LEGACY_ISSUER, roles, DEFAULT_TENANT_ID);
    }

    /** Variant for SAS-issued tokens (issuer = oidc.issuer-url). */
    public String sasToken(String sub, List<String> roles) {
        return tokenWithIssuer(sub, SAS_ISSUER, roles, DEFAULT_TENANT_ID);
    }

    /** Cross-tenant token used in regression tests. */
    public String tokenWithTenant(String sub, List<String> roles, String tenantId) {
        return tokenWithIssuer(sub, LEGACY_ISSUER, roles, tenantId);
    }

    public String tokenWithIssuer(String sub, String issuer, List<String> roles, String tenantId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("roles", roles);
        claims.put("iss", issuer);
        claims.put("tenant_id", tenantId);
        claims.put("iat", now);
        claims.put("exp", now.plus(30, ChronoUnit.MINUTES));
        return signer.sign(claims);
    }

    /** Builds a {@link JwtDecoder} suitable for slice tests. */
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }
}
