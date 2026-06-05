package com.example.admin.infrastructure.security;

import com.example.security.jwt.JwtVerificationException;
import com.example.security.jwt.JwtVerifier;
import com.example.security.jwt.Rs256JwtVerifier;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Objects;

/**
 * {@link JwtVerifier} that enforces {@code iss} on top of
 * {@link Rs256JwtVerifier}. For the admin IdP the active kid's public key
 * is used to verify signatures; verification across historic kids would
 * require a JWKS-backed verifier (out of scope for 029-1).
 */
public final class IssuerEnforcingJwtVerifier implements JwtVerifier {

    private final Rs256JwtVerifier delegate;
    private final String expectedIssuer;

    public IssuerEnforcingJwtVerifier(RSAPublicKey publicKey, String expectedIssuer) {
        this.delegate = new Rs256JwtVerifier(Objects.requireNonNull(publicKey, "publicKey"));
        this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "expectedIssuer");
    }

    /**
     * TASK-BE-040 — admin-service mints two token_types from the same kid:
     * {@code admin} (access) and {@code admin_refresh}. Signature/iss
     * verification is the same for both; per-endpoint code enforces the
     * required {@code token_type} after this verifier returns.
     */

    @Override
    public Map<String, Object> verify(String token) throws JwtVerificationException {
        Map<String, Object> claims = delegate.verify(token);
        Object iss = claims.get("iss");
        if (iss == null || !expectedIssuer.equals(iss.toString())) {
            throw new JwtVerificationException(
                    "Token verification failed: iss claim does not match admin IdP");
        }
        return claims;
    }
}
