package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
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
 *   <li>{@code refresh_token}: TASK-BE-274 cycle 3 — {@link SasRefreshTokenAuthenticationProvider}
 *       generates a brand-new JWT for the rotated access token using a fresh
 *       {@link org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext}
 *       whose {@code authorizationGrantType} is {@code refresh_token}. The previous
 *       assumption that SAS built-in reuses claims does not apply to our custom
 *       provider. Reuses {@code customizeForAuthorizationCode} logic: principal
 *       details map first, ClientSettings Option B fallback second.
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

    /**
     * TASK-BE-324: signed claim carrying the tenant's ACTIVE entitled domainKeys.
     * Must match the domain-side gates' {@code CLAIM_ENTITLED_DOMAINS} exactly.
     */
    private static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";

    /**
     * TASK-BE-324: account-service port used to resolve {@code entitled_domains} at
     * issuance time. Autowired by constructor injection.
     */
    private final AccountServicePort accountServicePort;

    public TenantClaimTokenCustomizer(AccountServicePort accountServicePort) {
        this.accountServicePort = accountServicePort;
    }

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
        } else if (AuthorizationGrantType.TOKEN_EXCHANGE.equals(grantType)) {
            // TASK-BE-327 (ADR-MONO-020 D2+D3): assume-tenant exchange. The
            // selected tenant + tenant_type are carried on the token context by
            // AssumeTenantAuthenticationProvider; inject them + the SELECTED
            // tenant's entitled_domains (D3, least-privilege, fail-soft).
            customizeForAssumeTenant(context);
        } else if (AuthorizationGrantType.REFRESH_TOKEN.equals(grantType)) {
            // TASK-BE-274 cycle 3: SasRefreshTokenAuthenticationProvider generates a
            // brand-new JWT for the rotated access token using a fresh TokenContext whose
            // authorizationGrantType is REFRESH_TOKEN. The previous no-op assumption
            // (SAS built-in reuse) does not apply to our custom provider — it calls
            // tokenGenerator.generate() which invokes this customizer. Without handling
            // REFRESH_TOKEN here the tenant_id / tenant_type claims are absent from the
            // rotated access token.
            //
            // Strategy: reuse customizeForAuthorizationCode logic which already handles
            // the dual fallback (principal.getDetails() map → ClientSettings Option B →
            // clientName legacy). The principal in the REFRESH_TOKEN context is the same
            // Authentication stored in the OAuth2Authorization at authorization_code time,
            // and the registeredClient carries custom.tenant_id / custom.tenant_type via
            // OAuthClientMapper (Option B). Either path resolves tenant_id correctly.
            customizeForAuthorizationCode(context);
        }
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
            populateEntitledDomains(context, tenantId);
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
                populateEntitledDomains(context, tenantInfo.tenantId());
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
     * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2+D3): assume-tenant exchange
     * branch. The SELECTED tenant + its {@code tenant_type} are carried on the
     * {@link AssumeTenantAuthenticationToken} (the context's authorizationGrant),
     * which {@code JwtGenerator} copies verbatim into the encoding context.
     * Injects {@code tenant_id=<selected>} + {@code tenant_type} and reuses the
     * keystone {@link #populateEntitledDomains} <b>verbatim</b> keyed on the
     * SELECTED tenant — least-privilege (NO union across the operator's other
     * assignments, D3) + fail-soft (account down → claim omitted) +
     * recursion-safe (this branch never runs on {@code client_credentials}).
     *
     * <p>Fail-closed on a missing selected tenant: the provider always sets the
     * context attributes, so a blank value here is a wiring bug — reject rather
     * than mint a tenant-less token (auth-service fails closed on missing tenant).
     */
    private void customizeForAssumeTenant(JwtEncodingContext context) {
        // The selected tenant is carried on the authorizationGrant (the
        // AssumeTenantAuthenticationToken), which JwtGenerator copies verbatim into
        // the JwtEncodingContext (unlike arbitrary context.put() attributes, which
        // it does NOT copy).
        String selectedTenantId = null;
        String selectedTenantType = null;
        if (context.getAuthorizationGrant() instanceof AssumeTenantAuthenticationToken grant) {
            selectedTenantId = grant.getSelectedTenantId();
            selectedTenantType = grant.getSelectedTenantType();
        }

        if (selectedTenantId == null || selectedTenantId.isBlank()
                || selectedTenantType == null || selectedTenantType.isBlank()) {
            log.error("SECURITY: assume-tenant token issued without selected tenant context. "
                    + "tenant_id={}, tenant_type={}", selectedTenantId, selectedTenantType);
            throw new IllegalStateException(
                    "selected tenant_id/tenant_type is required for assume-tenant issuance (fail-closed)");
        }

        context.getClaims()
                .claim("tenant_id", selectedTenantId)
                .claim("tenant_type", selectedTenantType);
        log.debug("TenantClaimTokenCustomizer: assume-tenant — injected tenant_id={}, tenant_type={}",
                selectedTenantId, selectedTenantType);

        // D3: SELECTED tenant's ACTIVE subscriptions ONLY (no union). Reused verbatim.
        populateEntitledDomains(context, selectedTenantId);
    }

    /**
     * TASK-BE-324 (ADR-MONO-019 § 3.3 keystone): populates the signed
     * {@code entitled_domains} claim from the tenant's ACTIVE domain subscriptions
     * (queried from account-service at issuance time). Only invoked from the
     * {@code authorization_code}/{@code refresh_token} path — never from
     * {@code client_credentials} (recursion safety: a cc issuance is what mints the
     * Bearer used to call account-service; if the cc path called account it would
     * re-invoke this customizer → infinite recursion).
     *
     * <p><b>fail-soft</b>: any failure (account-service down / circuit-open / timeout /
     * exception) OR an empty result → the claim is omitted and issuance proceeds with
     * the legacy {@code tenant_id} gate (net-zero). Token issuance must never depend on
     * account-service availability, so the exception is swallowed (logged at WARN).
     */
    private void populateEntitledDomains(JwtEncodingContext context, String tenantId) {
        try {
            java.util.List<String> entitled = accountServicePort.listEntitledDomains(tenantId);
            if (entitled != null && !entitled.isEmpty()) {
                context.getClaims().claim(CLAIM_ENTITLED_DOMAINS, entitled);
                log.debug("TenantClaimTokenCustomizer: injected entitled_domains={} for tenant_id={}",
                        entitled, tenantId);
            }
        } catch (RuntimeException e) {
            // fail-soft (ADR-MONO-019 keystone): token issuance must not depend on
            // account-service availability. Omit entitled_domains → legacy tenant_id
            // gate still applies (net-zero). Do NOT propagate.
            log.warn("TenantClaimTokenCustomizer: entitled_domains lookup failed for tenant_id={}, "
                            + "omitting claim (fail-soft): {}",
                    tenantId, e.toString());
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
