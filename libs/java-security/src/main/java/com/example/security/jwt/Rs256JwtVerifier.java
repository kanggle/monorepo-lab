package com.example.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;

import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RS256 implementation of {@link JwtVerifier} using JJWT.
 *
 * <p>Optional {@code iss} / {@code aud} claim enforcement is supported via the
 * three-arg constructor. When {@code expectedIssuer} or {@code expectedAudience}
 * is non-null, the parser requires the matching claim and rejects tokens whose
 * value differs (or whose claim is missing).
 *
 * <p>Thread-safe: all fields are immutable after construction.
 */
public final class Rs256JwtVerifier implements JwtVerifier {

    private final PublicKey publicKey;
    private final String expectedIssuer;
    private final String expectedAudience;

    /**
     * Construct a verifier that performs signature + expiry checks only.
     */
    public Rs256JwtVerifier(PublicKey publicKey) {
        this(publicKey, null, null);
    }

    /**
     * Construct a verifier that additionally requires {@code iss == expectedIssuer}.
     */
    public Rs256JwtVerifier(PublicKey publicKey, String expectedIssuer) {
        this(publicKey, expectedIssuer, null);
    }

    /**
     * Construct a verifier that additionally requires {@code iss == expectedIssuer}
     * (when non-null) and {@code aud == expectedAudience} (when non-null).
     *
     * @param publicKey         RSA public key for signature verification (required)
     * @param expectedIssuer    expected {@code iss} claim value, or {@code null} to skip
     * @param expectedAudience  expected {@code aud} claim value, or {@code null} to skip
     */
    public Rs256JwtVerifier(PublicKey publicKey, String expectedIssuer, String expectedAudience) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
    }

    @Override
    public Map<String, Object> verify(String token) throws JwtVerificationException {
        Objects.requireNonNull(token, "token must not be null");

        try {
            JwtParserBuilder parser = Jwts.parser().verifyWith(publicKey);
            if (expectedIssuer != null) {
                parser.requireIssuer(expectedIssuer);
            }
            if (expectedAudience != null) {
                parser.requireAudience(expectedAudience);
            }
            Claims claims = parser
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
