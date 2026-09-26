package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.session.PrincipalDetailKeys;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.security.Principal;
import java.util.Map;

/**
 * TASK-BE-604 — the tenant a SAS session was authenticated in: the tenant its tokens'
 * {@code tenant_id} claim carries, and therefore the tenant its {@code refresh_tokens}
 * mirror rows must carry.
 *
 * <p><b>Not the client's tenant.</b> The two differ whenever an account logs in through a
 * client of another tenant — today, the console client reached through the form-login
 * cross-tenant fallback (an ADR-MONO-044 D5 operator whose credential lives in a consumer
 * tenant), and any client reached by single sign-on from an existing browser session. The
 * refresh provider used to compare the mirror row with the client's tenant, so every refresh
 * of such a session tripped {@code TOKEN_TENANT_MISMATCH} and survived only because SAS's
 * built-in refresh provider re-ran the grant after ours (TASK-BE-604 AC-0).
 *
 * <p><b>The rule mirrors {@code TenantClaimTokenCustomizer#customizeForAuthorizationCode}
 * exactly</b>, because the first mirror row takes its tenant from the claim that method
 * mints ({@code DomainSyncOAuth2AuthorizationService#extractTenantId}): the resource-owner
 * principal's details {@link PrincipalDetailKeys#TENANT_ID} when it carries BOTH
 * {@code tenant_id} and {@code tenant_type}; otherwise the registered client's tenant. Any
 * other rule would make the first row and the rotated rows disagree again.
 */
final class AuthorizationSessionTenant {

    private AuthorizationSessionTenant() {
    }

    /**
     * @param authorization the SAS authorization being refreshed
     * @param clientTenant  the registered client's tenant, or {@code null}
     * @return the session tenant, or {@code null} when neither source has one
     */
    static String of(OAuth2Authorization authorization, String clientTenant) {
        Object principal = authorization.getAttribute(Principal.class.getName());
        if (principal instanceof Authentication authentication
                && authentication.getDetails() instanceof Map<?, ?> details) {
            String tenantId = nonBlank(details.get(PrincipalDetailKeys.TENANT_ID));
            String tenantType = nonBlank(details.get(PrincipalDetailKeys.TENANT_TYPE));
            if (tenantId != null && tenantType != null) {
                return tenantId;
            }
        }
        return clientTenant;
    }

    private static String nonBlank(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
