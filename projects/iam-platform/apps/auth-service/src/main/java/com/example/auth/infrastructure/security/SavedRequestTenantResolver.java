package com.example.auth.infrastructure.security;

import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.infrastructure.tenant.TenantTypeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

/**
 * TASK-BE-396 (ADR-006 option 1) — resolves the tenant a social-login principal
 * should be attributed to, from the OIDC client that <b>initiated</b> the SAS
 * browser flow.
 *
 * <p>Mechanism: at social-callback time the browser still carries the saved
 * {@code GET /oauth2/authorize?client_id=...} request in its
 * {@link HttpSessionRequestCache}. This resolver reads the {@code client_id}
 * from that saved request, looks the client up via
 * {@link RegisteredClientRepository}, and extracts
 * {@link OAuthClientMapper#SETTING_TENANT_ID} /
 * {@link OAuthClientMapper#SETTING_TENANT_TYPE} from its
 * {@link ClientSettings}. No state threading is needed — the saved request
 * already carries {@code client_id}.
 *
 * <p>Fallback (no saved request / no {@code client_id} / no matching client /
 * blank tenant settings) → {@link TenantContext#DEFAULT_TENANT_ID} +
 * {@link TenantTypeResolver#resolve(String)} (TASK-BE-407).
 *
 * <p>The resolved tenant feeds BOTH the social-identity row attribution (via
 * {@code SocialIdentityPersistStep}) and the SAS principal's {@code details}
 * map. Note the {@code roles} claim seed is keyed on the <i>client platform</i>
 * (its {@code tenant_id}) by {@code RoleSeedPolicy} independently — this
 * resolver supplies the same client-derived tenant, so a {@code web-store}
 * client yields {@code tenant_id=ecommerce} → {@code roles:[CUSTOMER]}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavedRequestTenantResolver {

    /** The OIDC authorization endpoint whose saved request carries {@code client_id}. */
    private static final String AUTHORIZE_PATH = "/oauth2/authorize";

    private final RegisteredClientRepository registeredClientRepository;
    private final TenantTypeResolver tenantTypeResolver;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    /**
     * Resolves the tenant + the saved-request redirect URL in one call.
     *
     * <p>Reads the saved request via {@link RequestCache#getRequest} (NOT
     * {@code removeRequest} — the form-login success handler must still see it).
     * The redirect URL is captured first, then the tenant is derived from the
     * saved {@code client_id}.
     *
     * @return a {@link Resolution} carrying the tenant and the redirect URL
     *         (redirect URL is {@code null} when there is no saved request)
     */
    public Resolution resolve(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);

        String redirectUrl = saved != null ? saved.getRedirectUrl() : null;
        String clientId = extractClientId(saved);

        TenantInfo tenant = resolveTenant(clientId);
        return new Resolution(tenant.tenantId(), tenant.tenantType(), redirectUrl);
    }

    private String extractClientId(SavedRequest saved) {
        if (saved == null) {
            return null;
        }
        // Only trust the client_id when it came from a saved /oauth2/authorize
        // request — guards against an arbitrary saved URL carrying a client_id param.
        String url = saved.getRedirectUrl();
        if (url == null || !url.contains(AUTHORIZE_PATH)) {
            return null;
        }
        var params = saved.getParameterValues("client_id");
        if (params == null || params.length == 0 || params[0] == null || params[0].isBlank()) {
            return null;
        }
        return params[0];
    }

    private TenantInfo resolveTenant(String clientId) {
        if (clientId != null) {
            RegisteredClient client = registeredClientRepository.findByClientId(clientId);
            if (client != null) {
                ClientSettings cs = client.getClientSettings();
                Object rawTenantId = cs.getSetting(OAuthClientMapper.SETTING_TENANT_ID);
                Object rawTenantType = cs.getSetting(OAuthClientMapper.SETTING_TENANT_TYPE);
                if (rawTenantId instanceof String tid && rawTenantType instanceof String ttype
                        && !tid.isBlank() && !ttype.isBlank()) {
                    log.debug("SavedRequestTenantResolver: resolved tenant_id={} tenant_type={} "
                            + "from initiating client_id={}", tid.trim(), ttype.trim(), clientId);
                    return new TenantInfo(tid.trim(), ttype.trim());
                }
            }
        }

        // TASK-BE-407: tenant_type via the resolver. For DEFAULT_TENANT_ID this is the
        // pre-seeded cache value (no network call), preserving prior hot-path behavior.
        String fallbackTenant = TenantContext.DEFAULT_TENANT_ID;
        String fallbackType = tenantTypeResolver.resolve(fallbackTenant);
        log.debug("SavedRequestTenantResolver: no initiating client tenant (client_id={}), "
                + "falling back to tenant_id={} tenant_type={}", clientId, fallbackTenant, fallbackType);
        return new TenantInfo(fallbackTenant, fallbackType);
    }

    /**
     * The resolved tenant attribution plus the saved-request redirect URL.
     *
     * @param tenantId    the resolved tenant id (never blank)
     * @param tenantType  the resolved tenant type (never blank)
     * @param redirectUrl the saved {@code /oauth2/authorize} URL to resume, or
     *                    {@code null} if there was no saved request
     */
    public record Resolution(String tenantId, String tenantType, String redirectUrl) {
    }

    private record TenantInfo(String tenantId, String tenantType) {
    }
}
