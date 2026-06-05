package com.example.admin.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Admin-service JWT signing/verification configuration.
 *
 * <p>Canonical source of truth for the operator IdP (see
 * {@code specs/services/admin-service/architecture.md} — "Admin IdP Boundary").
 * Wired via {@link AdminJwtSigningConfig} into the singleton
 * {@code operatorJwtSigner} and {@code operatorJwtVerifier} beans.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code activeSigningKid} — kid used for newly minted tokens. Must
 *       appear as a key in {@link #getSigningKeys()}.</li>
 *   <li>{@code signingKeys} — kid → PKCS#8 PEM private key. Multiple
 *       entries allowed during rotation grace period; all kids are exposed
 *       through the JWKS endpoint (public key only).</li>
 *   <li>{@code expectedTokenType} — {@code token_type} claim required by
 *       {@code OperatorAuthenticationFilter} (default {@code "admin"}).</li>
 *   <li>{@code issuer} — {@code iss} claim written on sign and enforced on
 *       verify (default {@code "admin-service"}).</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "admin.jwt")
public class AdminJwtProperties {

    @NotBlank
    private String activeSigningKid;

    @NotEmpty
    @NotNull
    private Map<String, String> signingKeys;

    @NotBlank
    private String expectedTokenType = "admin";

    @NotBlank
    private String issuer = "admin-service";

    /** Operator access token TTL (seconds). TASK-BE-029-3 default: 1 hour. */
    private long accessTokenTtlSeconds = 3600L;

    /** Operator refresh token TTL (seconds). TASK-BE-040 default: 30 days. */
    private long refreshTokenTtlSeconds = 2_592_000L;

    /** Token type written into refresh JWTs. TASK-BE-040. */
    @NotBlank
    private String refreshTokenType = "admin_refresh";

    public String getActiveSigningKid() {
        return activeSigningKid;
    }

    public void setActiveSigningKid(String activeSigningKid) {
        this.activeSigningKid = activeSigningKid;
    }

    public Map<String, String> getSigningKeys() {
        return signingKeys;
    }

    public void setSigningKeys(Map<String, String> signingKeys) {
        this.signingKeys = signingKeys;
    }

    public String getExpectedTokenType() {
        return expectedTokenType;
    }

    public void setExpectedTokenType(String expectedTokenType) {
        this.expectedTokenType = expectedTokenType;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String getRefreshTokenType() {
        return refreshTokenType;
    }

    public void setRefreshTokenType(String refreshTokenType) {
        this.refreshTokenType = refreshTokenType;
    }
}
