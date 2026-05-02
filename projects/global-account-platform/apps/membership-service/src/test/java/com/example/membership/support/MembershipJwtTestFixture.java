package com.example.membership.support;

import com.gap.security.jwt.Rs256JwtSigner;

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
 * Test fixture used by membership-service slice tests (TASK-BE-253).
 *
 * <p>Provides a self-contained RSA key pair, a JWT signer for issuing tokens
 * with arbitrary {@code iss} / {@code tenant_id} values, and a public key
 * accessor for the slice-test {@link org.springframework.security.oauth2.jwt.JwtDecoder}.
 */
public final class MembershipJwtTestFixture {

    public static final String LEGACY_ISSUER = "global-account-platform";
    public static final String SAS_ISSUER = "http://localhost:8081";
    public static final String DEFAULT_TENANT_ID = "fan-platform";

    private final Rs256JwtSigner signer;
    private final RSAPublicKey publicKey;

    public MembershipJwtTestFixture() {
        KeyPair kp = generateKeyPair();
        this.signer = new Rs256JwtSigner(kp.getPrivate(), "test-key-001");
        this.publicKey = (RSAPublicKey) kp.getPublic();
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String token(String sub, List<String> roles) {
        return tokenWithIssuer(sub, LEGACY_ISSUER, roles, DEFAULT_TENANT_ID);
    }

    public String sasToken(String sub, List<String> roles) {
        return tokenWithIssuer(sub, SAS_ISSUER, roles, DEFAULT_TENANT_ID);
    }

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

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
