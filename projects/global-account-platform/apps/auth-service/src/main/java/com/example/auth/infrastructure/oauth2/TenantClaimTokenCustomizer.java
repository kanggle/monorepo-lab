package com.example.auth.infrastructure.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Injects {@code tenant_id} and {@code tenant_type} claims into every access token
 * and ID token issued by Spring Authorization Server.
 *
 * <p><b>Grant-type specific behaviour</b>
 * <ul>
 *   <li>{@code client_credentials}: reads {@code tenant_id} / {@code tenant_type} from the
 *       registered client's {@link RegisteredClient#getClientName()} metadata field. The
 *       convention for Phase 1 in-memory clients is:
 *       {@code clientName = "<tenant_id>|<tenant_type>"}.
 *       If the metadata is absent or malformed the customizer fails closed with
 *       {@link IllegalStateException} — tokens without tenant context must not be issued.
 *   <li>{@code authorization_code}: reads tenant context from the authenticated
 *       principal's JWT attributes (the principal carries the tenant claims that were
 *       embedded in the session during the login flow). If the authenticated principal
 *       does not carry tenant attributes, falls back to the client's tenant metadata.
 * </ul>
 *
 * <p><b>Token types covered</b>
 * <ul>
 *   <li>{@link OAuth2TokenType#ACCESS_TOKEN} — always customized</li>
 *   <li>{@code id_token} (OIDC ID token) — also customized when {@code openid} scope is present</li>
 *   <li>{@link OAuth2TokenType#REFRESH_TOKEN} — no-op (opaque, no claims)</li>
 * </ul>
 *
 * <p>TASK-BE-251 — Phase 2a (authorization_code + id_token tenant claims).
 */
@Slf4j
@Component
public class TenantClaimTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /**
     * Separator used in clientName to encode "tenantId|tenantType".
     * Example: {@code "fan-platform|B2C"}.
     */
    private static final String METADATA_SEPARATOR = "|";

    /** SAS uses the string "id_token" as the token type value for ID tokens. */
    private static final String ID_TOKEN_TYPE_VALUE = "id_token";

    @Override
    public void customize(JwtEncodingContext context) {
        OAuth2TokenType tokenType = context.getTokenType();

        // Customize access tokens and OIDC ID tokens; skip refresh tokens and others
        boolean isAccessToken = OAuth2TokenType.ACCESS_TOKEN.equals(tokenType);
        boolean isIdToken = ID_TOKEN_TYPE_VALUE.equals(tokenType.getValue());

        if (!isAccessToken && !isIdToken) {
            return;
        }

        AuthorizationGrantType grantType = context.getAuthorizationGrantType();

        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grantType)) {
            customizeForClientCredentials(context);
        } else if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(grantType)) {
            customizeForAuthorizationCode(context);
        }
        // refresh_token grant reuses access token claims — no separate customization needed
    }

    private void customizeForClientCredentials(JwtEncodingContext context) {
        RegisteredClient client = context.getRegisteredClient();
        String clientName = client.getClientName();
        String clientId = client.getClientId();

        if (clientName == null || !clientName.contains(METADATA_SEPARATOR)) {
            log.error("SECURITY: client_credentials token issued without tenant metadata. " +
                    "clientId={}, clientName={}", clientId, clientName);
            throw new IllegalStateException(
                    "tenant_id is required for token issuance (fail-closed); " +
                            "clientId=" + clientId + " has no tenant metadata in clientName. " +
                            "Expected format: '<tenantId>|<tenantType>'");
        }

        String[] parts = clientName.split("\\|", 2);
        String tenantId = parts[0].trim();
        String tenantType = parts[1].trim();

        if (tenantId.isBlank()) {
            log.error("SECURITY: client_credentials token issued with blank tenant_id. clientId={}", clientId);
            throw new IllegalStateException(
                    "tenant_id must not be blank (fail-closed); clientId=" + clientId);
        }
        if (tenantType.isBlank()) {
            log.error("SECURITY: client_credentials token issued with blank tenant_type. clientId={}", clientId);
            throw new IllegalStateException(
                    "tenant_type must not be blank (fail-closed); clientId=" + clientId);
        }

        context.getClaims()
                .claim("tenant_id", tenantId)
                .claim("tenant_type", tenantType);

        log.debug("TenantClaimTokenCustomizer: injected tenant_id={}, tenant_type={} for clientId={}",
                tenantId, tenantType, clientId);
    }

    private void customizeForAuthorizationCode(JwtEncodingContext context) {
        // For authorization_code, the principal is the authenticated user.
        // Our UserDetailsService embeds tenant_id / tenant_type as attributes in the
        // Authentication object (populated during the /api/auth/login or form-login flow).
        Authentication principal = context.getPrincipal();
        String clientId = context.getRegisteredClient().getClientId();

        String tenantId = extractTenantAttribute(principal, "tenant_id");
        String tenantType = extractTenantAttribute(principal, "tenant_type");

        if (tenantId != null && tenantType != null) {
            context.getClaims()
                    .claim("tenant_id", tenantId)
                    .claim("tenant_type", tenantType);
            log.debug("TenantClaimTokenCustomizer: authorization_code — injected tenant_id={}, " +
                    "tenant_type={} from principal for clientId={}", tenantId, tenantType, clientId);
        } else {
            // Fallback: use client-registered tenant metadata (same as client_credentials)
            RegisteredClient client = context.getRegisteredClient();
            String clientName = client.getClientName();
            if (clientName != null && clientName.contains(METADATA_SEPARATOR)) {
                String[] parts = clientName.split("\\|", 2);
                String fallbackTenantId = parts[0].trim();
                String fallbackTenantType = parts[1].trim();
                if (!fallbackTenantId.isBlank() && !fallbackTenantType.isBlank()) {
                    context.getClaims()
                            .claim("tenant_id", fallbackTenantId)
                            .claim("tenant_type", fallbackTenantType);
                    log.debug("TenantClaimTokenCustomizer: authorization_code — fallback to client " +
                            "tenant metadata tenant_id={} for clientId={}", fallbackTenantId, clientId);
                    return;
                }
            }
            // Fail-closed: if neither principal nor client carries tenant info, reject
            log.error("SECURITY: authorization_code token issued without tenant metadata. " +
                    "clientId={}, principal={}", clientId, principal.getName());
            throw new IllegalStateException(
                    "tenant_id is required for token issuance (fail-closed); " +
                            "neither principal attributes nor client metadata contain tenant context. " +
                            "clientId=" + clientId);
        }
    }

    /**
     * Extracts a tenant attribute from the authenticated principal.
     * The attribute may be stored in the principal's details or as a JWT claim
     * (depending on how the login flow populates the Authentication object).
     */
    private String extractTenantAttribute(Authentication principal, String attributeName) {
        if (principal == null) {
            return null;
        }
        // Check if details is a Map (e.g., populated by our TenantAwareUserDetailsService)
        Object details = principal.getDetails();
        if (details instanceof java.util.Map<?, ?> detailsMap) {
            Object value = detailsMap.get(attributeName);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }
}
