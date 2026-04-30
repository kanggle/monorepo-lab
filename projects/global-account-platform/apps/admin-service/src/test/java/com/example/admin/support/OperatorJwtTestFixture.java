package com.example.admin.support;

import com.gap.security.jwt.JwtVerifier;
import com.gap.security.jwt.Rs256JwtSigner;
import com.gap.security.jwt.Rs256JwtVerifier;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight helper that generates an RSA key pair in-memory and mints
 * operator JWTs for slice/integration tests. Mints only the canonical claim
 * set required by rbac.md: {@code sub}, {@code jti}, {@code token_type=admin}
 * (plus standard {@code iss}/{@code iat}/{@code exp}).
 */
public final class OperatorJwtTestFixture {

    private final KeyPair keyPair;
    private final Rs256JwtSigner signer;
    private final Rs256JwtVerifier verifier;

    public OperatorJwtTestFixture() {
        this.keyPair = generateKeyPair();
        this.signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-key-001");
        this.verifier = new Rs256JwtVerifier(keyPair.getPublic());
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

    public JwtVerifier verifier() {
        return verifier;
    }

    /** Mints a canonical operator token with a freshly generated {@code jti}. */
    public String operatorToken(String sub) {
        return operatorToken(sub, UUID.randomUUID().toString(), "admin");
    }

    public String operatorToken(String sub, String jti) {
        return operatorToken(sub, jti, "admin");
    }

    public String operatorToken(String sub, String jti, String tokenType) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("jti", jti);
        claims.put("token_type", tokenType);
        claims.put("iss", "admin-service");
        claims.put("iat", now);
        claims.put("exp", now.plus(30, ChronoUnit.MINUTES));
        return signer.sign(claims);
    }

    /**
     * Mints an RS256-signed operator refresh token with the canonical claim
     * set required by {@code POST /api/admin/auth/refresh}. Used by slice
     * tests after TASK-BE-040-fix (the controller no longer tolerates
     * alg:none payloads).
     */
    public String signRefresh(String sub, String jti, Instant expiresAt) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("jti", jti);
        claims.put("token_type", "admin_refresh");
        claims.put("iss", "admin-service");
        claims.put("iat", now);
        claims.put("exp", expiresAt);
        return signer.sign(claims);
    }

    public String expiredToken(String sub) {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("token_type", "admin");
        claims.put("iss", "admin-service");
        claims.put("iat", past);
        claims.put("exp", past.plus(1, ChronoUnit.MINUTES));
        return signer.sign(claims);
    }
}
