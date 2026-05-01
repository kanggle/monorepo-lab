package com.example.auth.infrastructure.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Injects {@code tenant_id} and {@code tenant_type} claims into every access token
 * issued by Spring Authorization Server.
 *
 * <p><b>Grant-type specific behaviour</b>
 * <ul>
 *   <li>{@code client_credentials}: reads {@code tenant_id} / {@code tenant_type} from the
 *       registered client's {@link RegisteredClient#getClientName()} metadata field. The
 *       convention for Phase 1 in-memory clients is:
 *       {@code clientName = "<tenant_id>|<tenant_type>"}.
 *       If the metadata is absent or malformed the customizer fails closed with
 *       {@link IllegalStateException} — tokens without tenant context must not be issued.
 *   <li>{@code authorization_code} (Phase 2): will read tenant context from the authenticated
 *       principal's attributes. Not yet implemented — Phase 2 only.
 * </ul>
 *
 * <p>TASK-BE-251 — Phase 1 (client_credentials + in-memory placeholder).
 */
@Slf4j
@Component
public class TenantClaimTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /**
     * Separator used in clientName to encode "tenantId|tenantType".
     * Example: {@code "fan-platform|B2C"}.
     */
    private static final String METADATA_SEPARATOR = "|";

    @Override
    public void customize(JwtEncodingContext context) {
        // Only customize access tokens — id tokens and refresh tokens are handled separately
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }

        AuthorizationGrantType grantType = context.getAuthorizationGrantType();

        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grantType)) {
            customizeForClientCredentials(context);
        }
        // Phase 2: authorization_code — reads from authenticated principal (not yet implemented)
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
}
