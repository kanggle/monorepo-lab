package com.example.security.jwt;

import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * RS256 implementation of {@link JwtSigner} using JJWT.
 * <p>
 * Thread-safe: the private key and kid are immutable after construction.
 */
public final class Rs256JwtSigner implements JwtSigner {

    private final PrivateKey privateKey;
    private final String kid;

    /**
     * @param privateKey RSA private key for signing
     * @param kid        key ID to include in the JWT header
     */
    public Rs256JwtSigner(PrivateKey privateKey, String kid) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
        this.kid = Objects.requireNonNull(kid, "kid must not be null");
    }

    @Override
    public String sign(Map<String, Object> claims) {
        Objects.requireNonNull(claims, "claims must not be null");

        var builder = Jwts.builder()
                .header().keyId(kid).and();

        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case "sub" -> builder.subject(asString(value));
                case "iss" -> builder.issuer(asString(value));
                case "aud" -> builder.audience().add(asString(value)).and();
                case "exp" -> builder.expiration(toDate(value));
                case "iat" -> builder.issuedAt(toDate(value));
                case "nbf" -> builder.notBefore(toDate(value));
                case "jti" -> builder.id(asString(value));
                default -> builder.claim(key, value);
            }
        }

        return builder
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Date toDate(Object value) {
        if (value instanceof Date d) {
            return d;
        }
        if (value instanceof Instant i) {
            return Date.from(i);
        }
        if (value instanceof Long l) {
            return Date.from(Instant.ofEpochSecond(l));
        }
        if (value instanceof Number n) {
            return Date.from(Instant.ofEpochSecond(n.longValue()));
        }
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to Date");
    }
}
