package com.example.security.jwt;

import java.security.PrivateKey;
import java.util.Map;

/**
 * Signs JWT tokens using an asymmetric key (RS256).
 * <p>
 * Implementations must be thread-safe.
 */
public interface JwtSigner {

    /**
     * Creates a signed JWT with the given claims.
     *
     * @param claims  a map of JWT claims (e.g. sub, email, role, exp, iat, iss, aud)
     * @return a compact-serialized signed JWT string
     */
    String sign(Map<String, Object> claims);
}
