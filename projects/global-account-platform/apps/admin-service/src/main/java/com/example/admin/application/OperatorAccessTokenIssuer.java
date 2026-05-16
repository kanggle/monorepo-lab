package com.example.admin.application;

import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.example.admin.infrastructure.security.JwtSigner;
import com.example.common.id.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TASK-BE-298 — single source of truth for the operator <b>access</b> token
 * claim assembly. Extracted from {@code AdminLoginService#mintAccessToken} so
 * the password+TOTP login path (TASK-BE-029-3) and the GAP OIDC token-exchange
 * path (TASK-BE-298 / ADR-MONO-014 § D2) mint <b>byte-identical</b> tokens
 * through the <b>same</b> signer / signing key / claim set — no claim-assembly
 * duplication (architecture.md §Operator-Token Minting Paths; task
 * "reuse, do not duplicate claim assembly").
 *
 * <p>Emitted claims (rbac.md D4): {@code sub} (operator UUID v7),
 * {@code iss=admin-service}, {@code jti} (UUID v7), {@code token_type=admin},
 * {@code iat}, {@code exp = iat + access-token-ttl}. The RS256 signature + kid
 * are applied by {@link JwtSigner} (the admin self-issuing IdP key).
 *
 * <p><b>Scope invariant</b>: this issuer never reads or accepts a tenant
 * scope. Tenant scope is resolved at request time from
 * {@code admin_operators.tenant_id} (ADR-002 sentinel) by the RBAC /
 * tenant-scope evaluators — it is never carried in the operator JWT and never
 * derived from an exchanged OIDC token (ADR-MONO-014 D3).
 */
@Component
@RequiredArgsConstructor
public class OperatorAccessTokenIssuer {

    private final JwtSigner jwtSigner;
    private final AdminJwtProperties jwtProperties;

    /** TTL (seconds) of tokens minted by this issuer — the operator access TTL. */
    public long accessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtlSeconds();
    }

    /**
     * Mints the canonical operator access token for {@code operatorUuid}
     * ({@code admin_operators.operator_id}, UUID v7 — the JWT {@code sub}).
     * The caller MUST have already authenticated/resolved the operator; this
     * method performs no authorization.
     */
    public String mint(String operatorUuid) {
        Instant now = Instant.now();
        long ttl = jwtProperties.getAccessTokenTtlSeconds();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", operatorUuid);
        claims.put("iss", jwtProperties.getIssuer());
        claims.put("jti", UuidV7.randomString());
        claims.put("token_type", jwtProperties.getExpectedTokenType());
        claims.put("iat", now);
        claims.put("exp", now.plusSeconds(ttl));
        return jwtSigner.sign(claims);
    }
}
