package com.example.security.jwt;

import java.util.Map;

/**
 * Verifies signed JWT tokens and extracts claims.
 * <p>
 * Implementations must be thread-safe.
 */
public interface JwtVerifier {

    /**
     * Verifies the token signature and standard time-based claims (exp, nbf),
     * then returns the payload claims.
     *
     * @param token compact-serialized JWT string
     * @return claims map extracted from the verified token
     * @throws JwtVerificationException if the token is invalid, expired, or signed with an untrusted key
     */
    Map<String, Object> verify(String token) throws JwtVerificationException;
}
