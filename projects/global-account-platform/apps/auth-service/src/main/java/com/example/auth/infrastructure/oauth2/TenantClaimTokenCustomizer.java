package com.example.auth.infrastructure.oauth2;

import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Injects {@code tenant_id} and {@code tenant_type} claims into every access token
 * and ID token issued by Spring Authorization Server.
 *
 * <p><b>Grant-type specific behaviour</b>
 * <ul>
 *   <li>{@code client_credentials}: reads {@code tenant_id} / {@code tenant_type} from
 *       {@link ClientSettings} custom keys ({@link OAuthClientMapper#SETTING_TENANT_ID},
 *       {@link OAuthClientMapper#SETTING_TENANT_TYPE}) injected by
 *       {@link com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper}
 *       during client lookup. This is the Option B implementation (TASK-BE-252).
 *       If the client was built without the JPA mapper (e.g. in unit tests that still
 *       use the old {@code clientName = "tenantId|tenantType"} format), falls back to
 *       the clientName split for backward compatibility.
 *   <li>{@code authorization_code}: reads tenant context from the authenticated
 *       principal's JWT attributes. Falls back to ClientSettings if absent.
 * </ul>
 *
 * <p><b>Token types covered</b>
 * <ul>
 *   <li>{@link OAuth2TokenType#ACCESS_TOKEN} — always customized</li>
 *   <li>{@code id_token} (OIDC ID token) — also customized when {@code openid} scope present</li>
 *   <li>{@link OAuth2TokenType#REFRESH_TOKEN} — no-op (opaque, no claims)</li>
 * </ul>
 *
 * <p>TASK-BE-251 — Phase 2a initial implementation.
 * TASK-BE-252 — Option B: reads tenant info from ClientSettings instead of clientName.
 */
@Slf4j
@Component
public class TenantClaimTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /** Separator used in legacy clientName encoding (backward-compat fallback only). */
    private static final String METADATA_SEPARATOR = "|";

    /** SAS uses the string "id_token" as the token type value for ID tokens. */
    private static final String ID_TOKEN_TYPE_VALUE = "id_token";

    @Override
    public void customize(JwtEncodingContext context) {
        OAuth2TokenType tokenType = context.getTokenType();

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
        String clientId = client.getClientId();

        // Option B: prefer ClientSettings custom keys (set by JPA mapper)
        TenantInfo tenantInfo = extractTenantFromClientSettings(client);

        if (tenantInfo == null) {
            // Fallback: legacy clientName = "tenantId|tenantType" (backward compat for unit tests
            // and any RegisteredClient not built via the JPA mapper)
            tenantInfo = extractTenantFromClientNameOrFail(client, clientId);
        }

        context.getClaims()
                .claim("tenant_id", tenantInfo.tenantId())
                .claim("tenant_type", tenantInfo.tenantType());

        log.debug("TenantClaimTokenCustomizer: injected tenant_id={}, tenant_type={} for clientId={}",
                tenantInfo.tenantId(), tenantInfo.tenantType(), clientId);
    }

    /**
     * Reads tenant info from {@code clientName = "tenantId|tenantType"} with specific
     * fail-closed error messages for blank tenantId / tenantType.
     * Used when the ClientSettings path found no custom keys.
     */
    private TenantInfo extractTenantFromClientNameOrFail(RegisteredClient client, String clientId) {
        String clientName = client.getClientName();
        if (clientName == null || !clientName.contains(METADATA_SEPARATOR)) {
            log.error("SECURITY: client_credentials token issued without tenant metadata. " +
                    "clientId={}, clientName={}", clientId, clientName);
            throw new IllegalStateException(
                    "tenant_id is required for token issuance (fail-closed); " +
                            "clientId=" + clientId + " has no tenant metadata in ClientSettings or clientName. " +
                            "Expected format: '<tenantId>|<tenantType>'");
        }

        String[] parts = clientName.split("\\|", 2);
        String tenantId = parts[0].trim();
        String tenantType = parts.length > 1 ? parts[1].trim() : "";

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
        return new TenantInfo(tenantId, tenantType);
    }

    private void customizeForAuthorizationCode(JwtEncodingContext context) {
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
            // Fallback: client metadata from ClientSettings (Option B) or clientName (legacy)
            RegisteredClient client = context.getRegisteredClient();
            TenantInfo tenantInfo = extractTenantFromClientSettings(client);
            if (tenantInfo == null) {
                tenantInfo = extractTenantFromClientName(client);
            }

            if (tenantInfo != null && !tenantInfo.tenantId().isBlank() && !tenantInfo.tenantType().isBlank()) {
                context.getClaims()
                        .claim("tenant_id", tenantInfo.tenantId())
                        .claim("tenant_type", tenantInfo.tenantType());
                log.debug("TenantClaimTokenCustomizer: authorization_code — fallback to client " +
                        "tenant metadata tenant_id={} for clientId={}", tenantInfo.tenantId(), clientId);
            } else {
                log.error("SECURITY: authorization_code token issued without tenant metadata. " +
                        "clientId={}, principal={}", clientId, principal.getName());
                throw new IllegalStateException(
                        "tenant_id is required for token issuance (fail-closed); " +
                                "neither principal attributes nor client metadata contain tenant context. " +
                                "clientId=" + clientId);
            }
        }
    }

    /**
     * Reads tenant info from the client's {@link ClientSettings} custom keys.
     * Returns null if either key is missing, signalling the caller to try the fallback.
     */
    private TenantInfo extractTenantFromClientSettings(RegisteredClient client) {
        ClientSettings cs = client.getClientSettings();
        Object rawTenantId = cs.getSetting(OAuthClientMapper.SETTING_TENANT_ID);
        Object rawTenantType = cs.getSetting(OAuthClientMapper.SETTING_TENANT_TYPE);

        if (rawTenantId instanceof String tid && rawTenantType instanceof String ttype
                && !tid.isBlank() && !ttype.isBlank()) {
            return new TenantInfo(tid.trim(), ttype.trim());
        }
        return null;
    }

    /**
     * Legacy fallback: reads tenant info from {@code clientName = "tenantId|tenantType"}.
     * Used for RegisteredClient instances built without the JPA mapper (e.g. some unit tests).
     * Returns null if the format is absent or malformed.
     */
    private TenantInfo extractTenantFromClientName(RegisteredClient client) {
        String clientName = client.getClientName();
        if (clientName != null && clientName.contains(METADATA_SEPARATOR)) {
            String[] parts = clientName.split("\\|", 2);
            String tid = parts[0].trim();
            String ttype = parts.length > 1 ? parts[1].trim() : "";
            if (!tid.isBlank() && !ttype.isBlank()) {
                return new TenantInfo(tid, ttype);
            }
        }
        return null;
    }

    /**
     * Extracts a tenant attribute from the authenticated principal's details map.
     */
    private String extractTenantAttribute(Authentication principal, String attributeName) {
        if (principal == null) {
            return null;
        }
        Object details = principal.getDetails();
        if (details instanceof java.util.Map<?, ?> detailsMap) {
            Object value = detailsMap.get(attributeName);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    /** Value object carrying the two required tenant fields. */
    private record TenantInfo(String tenantId, String tenantType) {}
}
