package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.tenant.TenantContext;
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
 *
 * <p><b>TASK-BE-615 — consumer-pool principal.</b> A principal whose details tenant is the reserved
 * {@code consumer-pool} is mapped onto the requesting client's tenant (except the console and the
 * pool itself — {@link #mapsPoolPrincipalTo}). The claim minted for it is that site
 * ({@code TenantClaimTokenCustomizer}), so the first mirror row carries the site, and the refresh
 * comparison here must answer the site too: a store authorization compares with {@code ecommerce},
 * a fan authorization with {@code fan-platform} — a store refresh token can never satisfy a fan
 * comparison ({@code TOKEN_TENANT_MISMATCH} kept, AC-5). Every non-pool principal: byte-unchanged.
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
        return principal instanceof Authentication authentication
                ? of(authentication, clientTenant)
                : clientTenant;
    }

    /**
     * TASK-BE-605 — the same rule applied to a live browser-session principal, before any
     * {@link OAuth2Authorization} exists. {@link AuthorizeSessionTenantGate} uses it to decide
     * at {@code /oauth2/authorize} which tenant the code it is about to issue would carry; a
     * second copy of the rule there would be the drift this class exists to prevent.
     *
     * @param principal    the authenticated resource owner
     * @param clientTenant the registered client's tenant, or {@code null}
     * @return the session tenant, or {@code null} when neither source has one
     */
    static String of(Authentication principal, String clientTenant) {
        if (principal.getDetails() instanceof Map<?, ?> details) {
            String tenantId = nonBlank(details.get(PrincipalDetailKeys.TENANT_ID));
            String tenantType = nonBlank(details.get(PrincipalDetailKeys.TENANT_TYPE));
            if (tenantId != null && tenantType != null) {
                if (TenantContext.isConsumerPool(tenantId) && mapsPoolPrincipalTo(clientTenant)) {
                    // TASK-BE-615 — a pool principal's session tenant is the requesting client's
                    // site (multi-tenancy.md § 소비자 계정 풀 § 4); the pool value is storage only.
                    // Trimmed like the claim the issuer mints for it, so the mirror rows agree.
                    return clientTenant.trim();
                }
                return tenantId;
            }
        }
        return clientTenant;
    }

    /**
     * TASK-BE-615 — whether {@code principal} is a consumer-pool principal: its details carry the
     * reserved {@link TenantContext#CONSUMER_POOL_TENANT_ID} as the login-time tenant (the tenant of
     * the credential row the form login picked).
     */
    static boolean isPoolPrincipal(Authentication principal) {
        return principal != null
                && principal.getDetails() instanceof Map<?, ?> details
                && TenantContext.isConsumerPool(nonBlank(details.get(PrincipalDetailKeys.TENANT_ID)));
    }

    /**
     * TASK-BE-615 — the clients a pool principal is mapped onto: every client with a tenant except
     * the console ({@code iam}, contract D1 — a pool account is not an operator; the session keeps
     * the pool value there, so the issuer's pool refusal mints nothing) and the pool tenant itself.
     *
     * <p>Mapping does not mean admission: issuance mints a site token only for an ACTIVE membership
     * of that site ({@code TenantClaimTokenCustomizer}), and the authorize gate decides re-
     * authentication for non-consumer tenants ({@code AuthorizeSessionTenantGate}). This rule only
     * keeps the three readers — claim, mirror row, gate — on one value, which is what this class is for.
     */
    static boolean mapsPoolPrincipalTo(String clientTenant) {
        return TenantContext.poolPrincipalMapsTo(clientTenant);
    }

    private static String nonBlank(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
