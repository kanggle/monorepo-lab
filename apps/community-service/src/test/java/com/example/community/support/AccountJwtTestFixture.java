package com.example.community.support;

import com.gap.security.jwt.JwtVerifier;
import com.gap.security.jwt.Rs256JwtSigner;
import com.gap.security.jwt.Rs256JwtVerifier;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AccountJwtTestFixture {

    public static final String EXPECTED_ISSUER = "global-account-platform";

    private final Rs256JwtSigner signer;
    private final Rs256JwtVerifier verifier;

    public AccountJwtTestFixture() {
        KeyPair kp = generateKeyPair();
        this.signer = new Rs256JwtSigner(kp.getPrivate(), "test-key-001");
        this.verifier = new Rs256JwtVerifier(kp.getPublic(), EXPECTED_ISSUER);
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

    public String token(String sub, List<String> roles) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("roles", roles);
        claims.put("iss", EXPECTED_ISSUER);
        claims.put("iat", now);
        claims.put("exp", now.plus(30, ChronoUnit.MINUTES));
        return signer.sign(claims);
    }
}
