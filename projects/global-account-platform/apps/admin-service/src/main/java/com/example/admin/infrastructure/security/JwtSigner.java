package com.example.admin.infrastructure.security;

import com.gap.security.jwt.Rs256JwtSigner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Operator JWT signer: thin wrapper over the shared {@link Rs256JwtSigner}
 * that injects the configured issuer and active {@code kid} for every token
 * minted by admin-service.
 *
 * <p>Not the same type as {@code com.gap.security.jwt.JwtSigner} — this is
 * admin-service's infrastructure-level seam so downstream call sites do not
 * need to know about kid selection or issuer enforcement.
 */
public final class JwtSigner {

    private final AdminJwtKeyStore keyStore;
    private final String issuer;
    private final Rs256JwtSigner delegate;

    public JwtSigner(AdminJwtKeyStore keyStore, String issuer) {
        this.keyStore = Objects.requireNonNull(keyStore, "keyStore must not be null");
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.delegate = new Rs256JwtSigner(keyStore.activePrivateKey(), keyStore.activeKid());
    }

    /**
     * Produces a compact JWS with header {@code kid} set to the active kid
     * and the project-wide {@code iss} claim injected if absent.
     *
     * @param claims caller-provided claims; must not be null
     * @return compact-serialised signed JWT
     */
    public String sign(Map<String, Object> claims) {
        Objects.requireNonNull(claims, "claims must not be null");
        Map<String, Object> effective = new LinkedHashMap<>(claims);
        effective.putIfAbsent("iss", issuer);
        return delegate.sign(effective);
    }

    public String activeKid() {
        return keyStore.activeKid();
    }

    /**
     * Convenience helper for the operator refresh JWT (TASK-BE-040). Mints
     * a token with the canonical claim set required by
     * {@code POST /api/admin/auth/refresh}: {@code sub}, {@code jti},
     * {@code token_type=admin_refresh}, plus standard {@code iss}/{@code iat}/{@code exp}.
     */
    public String signRefresh(String operatorUuid,
                              String jti,
                              String tokenType,
                              java.time.Instant issuedAt,
                              java.time.Instant expiresAt) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", Objects.requireNonNull(operatorUuid, "operatorUuid"));
        claims.put("iss", issuer);
        claims.put("jti", Objects.requireNonNull(jti, "jti"));
        claims.put("token_type", Objects.requireNonNull(tokenType, "tokenType"));
        claims.put("iat", Objects.requireNonNull(issuedAt, "issuedAt"));
        claims.put("exp", Objects.requireNonNull(expiresAt, "expiresAt"));
        return delegate.sign(claims);
    }
}
