package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.AccountProfileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Maps an authenticated principal to an OIDC {@link OidcUserInfo} payload for
 * the {@code GET /oauth2/userinfo} endpoint.
 *
 * <p>The mapper is wired into the SAS {@code OidcUserInfoEndpointConfigurer} as the
 * {@code userInfoMapper} function. SAS calls it whenever a bearer token with
 * {@code scope=openid} is presented at the userinfo endpoint.</p>
 *
 * <p><b>Claim mapping (OIDC Core 1.0 § 5.1)</b>
 * <table>
 *   <tr><th>OIDC Claim</th><th>Source</th></tr>
 *   <tr><td>{@code sub}</td><td>JWT {@code sub} claim (accountId)</td></tr>
 *   <tr><td>{@code email}</td><td>{@link AccountProfileResult#email()}</td></tr>
 *   <tr><td>{@code email_verified}</td><td>{@link AccountProfileResult#emailVerified()}</td></tr>
 *   <tr><td>{@code name}</td><td>{@link AccountProfileResult#displayName()}</td></tr>
 *   <tr><td>{@code preferred_username}</td><td>{@link AccountProfileResult#preferredUsername()}</td></tr>
 *   <tr><td>{@code locale}</td><td>{@link AccountProfileResult#locale()}</td></tr>
 *   <tr><td>{@code tenant_id}</td><td>{@link AccountProfileResult#tenantId()}</td></tr>
 *   <tr><td>{@code tenant_type}</td><td>{@link AccountProfileResult#tenantType()}</td></tr>
 * </table>
 *
 * <p>If account-service is unavailable or returns empty, the userinfo response
 * contains only the {@code sub} claim — this is compliant per OIDC Core § 5.3.
 * Resource servers that need richer profile data should re-fetch via account-service
 * directly after token validation.</p>
 *
 * <p>TASK-BE-251 Phase 2a — authorization_code + PKCE + /oauth2/userinfo.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcUserInfoMapper implements Function<OidcUserInfoAuthenticationContext, OidcUserInfo> {

    private final AccountServicePort accountServicePort;

    @Override
    public OidcUserInfo apply(OidcUserInfoAuthenticationContext context) {
        OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
        // The principal is the Authorization (inner authentication) — it carries the JWT sub claim.
        // SAS stores the resource-owner Authentication inside the token; its name is the sub claim.
        org.springframework.security.core.Authentication principal =
                (org.springframework.security.core.Authentication) authentication.getPrincipal();
        String accountId = principal.getName();

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", accountId);

        try {
            Optional<AccountProfileResult> profileOpt = accountServicePort.getAccountProfile(accountId);
            profileOpt.ifPresentOrElse(
                    profile -> populateClaims(claims, profile),
                    () -> log.warn("OidcUserInfoMapper: no profile found for accountId={}", accountId)
            );
        } catch (Exception e) {
            // Fail-open: return minimal userinfo (sub only) rather than 503
            // to avoid breaking OIDC flows when account-service is temporarily down.
            log.error("OidcUserInfoMapper: account-service unavailable for accountId={}, " +
                    "returning sub-only userinfo. Cause: {}", accountId, e.getMessage());
        }

        return new OidcUserInfo(claims);
    }

    private void populateClaims(Map<String, Object> claims, AccountProfileResult profile) {
        putIfNotNull(claims, "email", profile.email());
        putIfNotNull(claims, "email_verified", profile.emailVerified());
        putIfNotNull(claims, "name", profile.displayName());
        putIfNotNull(claims, "preferred_username", profile.preferredUsername());
        putIfNotNull(claims, "locale", profile.locale());
        // Custom multi-tenant claims
        putIfNotNull(claims, "tenant_id", profile.tenantId());
        putIfNotNull(claims, "tenant_type", profile.tenantType());
    }

    private void putIfNotNull(Map<String, Object> claims, String key, Object value) {
        if (value != null) {
            claims.put(key, value);
        }
    }
}
