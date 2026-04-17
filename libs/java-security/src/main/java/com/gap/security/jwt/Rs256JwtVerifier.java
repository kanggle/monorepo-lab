package com.gap.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RS256 implementation of {@link JwtVerifier} using JJWT.
 * <p>
 * Thread-safe: the public key is immutable after construction.
 */
public final class Rs256JwtVerifier implements JwtVerifier {

    private final PublicKey publicKey;

    /**
     * @param publicKey RSA public key for signature verification
     */
    public Rs256JwtVerifier(PublicKey publicKey) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
    }

    @Override
    public Map<String, Object> verify(String token) throws JwtVerificationException {
        Objects.requireNonNull(token, "token must not be null");

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new LinkedHashMap<>(claims);
        } catch (ExpiredJwtException e) {
            throw new JwtVerificationException("Token has expired", e);
        } catch (JwtException e) {
            throw new JwtVerificationException("Token verification failed: " + e.getMessage(), e);
        }
    }
}
