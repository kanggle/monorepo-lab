package com.example.auth.application.port;

import com.example.auth.application.exception.TokenParseException;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.TokenPair;

import java.time.Instant;

/**
 * Port interface for generating JWT token pairs.
 * Implementation lives in infrastructure/jwt/.
 */
public interface TokenGeneratorPort {

    /**
     * Generates a new access + refresh token pair with tenant context.
     *
     * <p>Tenant claims ({@code tenant_id}, {@code tenant_type}) are required per
     * specs/features/multi-tenancy.md §JWT Changes. The implementation must
     * fail-closed if {@code tenantContext} is null or carries blank values.
     *
     * @param accountId     the account ID to embed as {@code sub}
     * @param scope         the {@code scope} claim (e.g. "user", "admin")
     * @param deviceId      the {@code device_id} claim (opaque UUID v7); may be null
     * @param tenantContext tenant_id and tenant_type — must not be null
     * @return token pair with access token, refresh token, and access TTL
     */
    TokenPair generateTokenPair(String accountId, String scope, String deviceId,
                                TenantContext tenantContext);

    /**
     * Generates a new access + refresh token pair without an attached device session.
     * Equivalent to {@link #generateTokenPair(String, String, String, TenantContext)}
     * with {@code deviceId = null}.
     */
    default TokenPair generateTokenPair(String accountId, String scope, TenantContext tenantContext) {
        return generateTokenPair(accountId, scope, null, tenantContext);
    }

    /**
     * @deprecated Use {@link #generateTokenPair(String, String, String, TenantContext)}.
     *             Retained for legacy callers that have not yet adopted tenant context;
     *             defaults to fan-platform / B2C_CONSUMER.
     */
    @Deprecated
    default TokenPair generateTokenPair(String accountId, String scope, String deviceId) {
        return generateTokenPair(accountId, scope, deviceId, TenantContext.defaultContext());
    }

    /**
     * @deprecated Use {@link #generateTokenPair(String, String, TenantContext)}.
     */
    @Deprecated
    default TokenPair generateTokenPair(String accountId, String scope) {
        return generateTokenPair(accountId, scope, null, TenantContext.defaultContext());
    }

    /**
     * Returns the access token TTL in seconds.
     */
    long accessTokenTtlSeconds();

    /**
     * Returns the refresh token TTL in seconds.
     */
    long refreshTokenTtlSeconds();

    /**
     * Extracts the JTI from a refresh token string.
     *
     * @throws TokenParseException if the token is null, blank, malformed, expired, or has an invalid signature
     */
    String extractJti(String refreshToken);

    /**
     * Extracts the account ID (sub) from a refresh token string.
     *
     * @throws TokenParseException if the token is null, blank, malformed, expired, or has an invalid signature
     */
    String extractAccountId(String refreshToken);

    /**
     * Extracts the issued-at (iat) instant from a refresh token string.
     *
     * @throws TokenParseException if the token is unparseable or does not contain an {@code iat} claim
     */
    Instant extractIssuedAt(String refreshToken);

    /**
     * Extracts the {@code tenant_id} claim from a token string.
     * Returns null if the claim is absent (legacy token without tenant context).
     *
     * @throws TokenParseException if the token is unparseable
     */
    String extractTenantId(String token);
}
