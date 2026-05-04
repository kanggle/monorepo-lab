package com.example.auth.infrastructure.jwt;

import com.example.auth.application.exception.TokenParseException;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.TokenPair;
import com.example.security.jwt.JwtSigner;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT token generator that produces RS256-signed access and refresh tokens.
 *
 * <p>TASK-BE-229: access token now includes required {@code tenant_id} and
 * {@code tenant_type} claims per specs/features/multi-tenancy.md §JWT Changes.
 * Issuing a token with null/blank tenant values is a fail-closed guard —
 * {@link IllegalStateException} is thrown before any signing attempt.
 */
@Slf4j
@Component
public class JwtTokenGenerator implements TokenGeneratorPort {

    private final JwtSigner jwtSigner;
    private final PublicKey publicKey;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenGenerator(
            JwtSigner jwtSigner,
            PublicKey publicKey,
            @Value("${auth.jwt.issuer}") String issuer,
            @Value("${auth.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
            @Value("${auth.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.jwtSigner = jwtSigner;
        this.publicKey = publicKey;
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    public TokenPair generateTokenPair(String accountId, String scope, String deviceId,
                                       TenantContext tenantContext) {
        // fail-closed: tenant_id and tenant_type are mandatory per multi-tenancy spec
        if (tenantContext == null) {
            log.error("SECURITY: attempted to issue JWT without tenant context for accountId={}", accountId);
            throw new IllegalStateException(
                    "tenant_id is required for token issuance (fail-closed); accountId=" + accountId);
        }
        if (tenantContext.tenantId() == null || tenantContext.tenantId().isBlank()) {
            log.error("SECURITY: attempted to issue JWT with blank tenant_id for accountId={}", accountId);
            throw new IllegalStateException(
                    "tenant_id must not be null or blank (fail-closed); accountId=" + accountId);
        }
        if (tenantContext.tenantType() == null || tenantContext.tenantType().isBlank()) {
            log.error("SECURITY: attempted to issue JWT with blank tenant_type for accountId={}", accountId);
            throw new IllegalStateException(
                    "tenant_type must not be null or blank (fail-closed); accountId=" + accountId);
        }

        Instant now = Instant.now();
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        // Build access token claims
        Map<String, Object> accessClaims = new LinkedHashMap<>();
        accessClaims.put("sub", accountId);
        accessClaims.put("iss", issuer);
        accessClaims.put("iat", now);
        accessClaims.put("exp", now.plusSeconds(accessTokenTtlSeconds));
        accessClaims.put("jti", accessJti);
        accessClaims.put("scope", scope);
        // tenant claims — required, fail-closed above ensures they are present
        accessClaims.put("tenant_id", tenantContext.tenantId());
        accessClaims.put("tenant_type", tenantContext.tenantType());
        if (deviceId != null) {
            // device_id claim: opaque UUID v7 of the device session that owns this access token.
            // See specs/contracts/http/auth-api.md "Access Token claims" + device-session.md D5.
            accessClaims.put("device_id", deviceId);
        }

        String accessToken = jwtSigner.sign(accessClaims);

        // Build refresh token claims — tenant_id embedded for rotation tenant validation
        Map<String, Object> refreshClaims = new LinkedHashMap<>();
        refreshClaims.put("sub", accountId);
        refreshClaims.put("jti", refreshJti);
        refreshClaims.put("iat", now);
        refreshClaims.put("exp", now.plusSeconds(refreshTokenTtlSeconds));
        refreshClaims.put("type", "refresh");
        refreshClaims.put("tenant_id", tenantContext.tenantId());

        String refreshToken = jwtSigner.sign(refreshClaims);

        return new TokenPair(accessToken, refreshToken, accessTokenTtlSeconds);
    }

    @Override
    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    @Override
    public long refreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    @Override
    public String extractJti(String refreshToken) {
        return parseClaims(refreshToken).getId();
    }

    @Override
    public String extractAccountId(String refreshToken) {
        return parseClaims(refreshToken).getSubject();
    }

    @Override
    public Instant extractIssuedAt(String refreshToken) {
        java.util.Date iat = parseClaims(refreshToken).getIssuedAt();
        if (iat == null) {
            throw new TokenParseException("JWT missing iat claim");
        }
        return iat.toInstant();
    }

    @Override
    public String extractTenantId(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get("tenant_id", String.class);
        } catch (TokenParseException e) {
            // propagate parse errors
            throw e;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenParseException(e.getMessage(), e);
        }
    }
}
