package com.example.admin.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TASK-BE-298 / ADR-MONO-014 — configuration for validating the GAP OIDC
 * {@code platform-console-web} subject token presented to
 * {@code POST /api/admin/auth/token-exchange}.
 *
 * <p>This is the auth-service (SAS) OIDC trust anchor — a DIFFERENT key space
 * from the admin-service self-issuing operator IdP
 * ({@code AdminJwtProperties} / {@code /.well-known/admin/jwks.json}). The two
 * are never conflated (security.md §GAP OIDC Subject-Token Validation).
 *
 * <ul>
 *   <li>{@code jwksUri} — auth-service JWKS endpoint
 *       ({@code /internal/auth/jwks}; same endpoint the gateway uses).</li>
 *   <li>{@code issuer} — expected {@code iss} (auth-service
 *       {@code oidc.issuer-url}).</li>
 *   <li>{@code audience} — expected {@code aud} member; the
 *       {@code platform-console-web} client_id (SAS sets the access-token
 *       {@code aud} to the registered client_id).</li>
 *   <li>{@code clockSkewSeconds} — ±tolerance for {@code exp}/{@code nbf}
 *       (jwt-standard-claims.md "Clock Skew" 60s recommendation).</li>
 *   <li>{@code connectTimeoutMs}/{@code readTimeoutMs} — JWKS fetch timeouts;
 *       on fetch failure the validator is fail-closed (rejects → 401, never
 *       trusts an unverified token).</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "admin.oidc")
public class GapOidcProperties {

    @NotBlank
    private String jwksUri = "http://localhost:8081/internal/auth/jwks";

    @NotBlank
    private String issuer = "http://localhost:8081";

    @NotBlank
    private String audience = "platform-console-web";

    @Positive
    private long clockSkewSeconds = 60L;

    @Positive
    private int connectTimeoutMs = 3000;

    @Positive
    private int readTimeoutMs = 5000;

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
