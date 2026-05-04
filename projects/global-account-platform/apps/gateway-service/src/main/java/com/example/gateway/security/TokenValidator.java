package com.example.gateway.security;

import com.example.security.jwt.JwtVerificationException;
import com.example.security.jwt.Rs256JwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.util.Map;

/**
 * Validates JWT tokens using JWKS-resolved public keys.
 * Extracts kid from JWT header, resolves the public key via JwksCache,
 * and verifies signature + exp/nbf claims.
 */
@Slf4j
@Component
public class TokenValidator {

    private final JwksCache jwksCache;
    private final String expectedIssuer;

    public TokenValidator(JwksCache jwksCache,
                          @org.springframework.beans.factory.annotation.Value("${gateway.jwt.expected-issuer}") String expectedIssuer) {
        this.jwksCache = jwksCache;
        this.expectedIssuer = expectedIssuer;
    }

    /**
     * Validates the JWT token and returns the claims if valid.
     *
     * @param token the compact JWT string (without "Bearer " prefix)
     * @return Mono of claims map on success, Mono.error on failure
     */
    public Mono<Map<String, Object>> validate(String token) {
        return Mono.fromCallable(() -> extractKid(token))
                .flatMap(kid -> jwksCache.getPublicKey(kid)
                        .flatMap(optKey -> {
                            if (optKey.isEmpty()) {
                                // kid not found, try refresh
                                return jwksCache.refreshJwks()
                                        .flatMap(keys -> {
                                            PublicKey key = keys.get(kid);
                                            if (key == null) {
                                                return Mono.error(new JwtVerificationException(
                                                        "No matching key found for kid: " + kid));
                                            }
                                            return verifyWithKey(token, key);
                                        });
                            }
                            return verifyWithKey(token, optKey.get());
                        }));
    }

    private String extractKid(String token) {
        // Parse the header without verification to get kid
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new JwtVerificationException("Invalid JWT format");
        }
        try {
            String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
            @SuppressWarnings("unchecked")
            Map<String, Object> header = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(headerJson, Map.class);
            String kid = (String) header.get("kid");
            if (kid == null || kid.isBlank()) {
                throw new JwtVerificationException("JWT missing kid header");
            }
            return kid;
        } catch (JwtVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtVerificationException("Failed to extract kid from JWT header", e);
        }
    }

    private Mono<Map<String, Object>> verifyWithKey(String token, PublicKey publicKey) {
        return Mono.fromCallable(() -> {
            Rs256JwtVerifier verifier = new Rs256JwtVerifier(publicKey, expectedIssuer);
            return verifier.verify(token);
        });
    }
}
