package com.example.auth.application.port;

import com.example.auth.application.result.AccountProfileResult;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.SocialSignupResult;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for communicating with account-service.
 * Implementation lives in infrastructure/client/.
 */
public interface AccountServicePort {

    /**
     * Looks up an account's current status by id.
     *
     * <p>TASK-BE-063: replaces the previous email-based credential lookup. The
     * login path now resolves email → credential locally, then calls this to
     * verify the account is still ACTIVE.</p>
     *
     * @param accountId the account to check
     * @return the account's status, or empty if the account does not exist
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    Optional<AccountStatusLookupResult> getAccountStatus(String accountId);

    /**
     * Creates or retrieves an account for social login via internal HTTP to account-service.
     * If an account with the given email already exists, returns the existing accountId.
     * If not, creates a new account and returns the new accountId.
     *
     * @param email          the user's email from the OAuth provider
     * @param provider       the OAuth provider name (e.g., "GOOGLE", "KAKAO")
     * @param providerUserId the user's unique ID from the OAuth provider
     * @param displayName    the user's display name from the OAuth provider (nullable)
     * @return social signup result with accountId, status, and whether it's a new account
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    SocialSignupResult socialSignup(String email, String provider, String providerUserId, String displayName);

    /**
     * Retrieves the full profile of an account for OIDC userinfo response construction.
     *
     * <p>Called by {@link com.example.auth.infrastructure.oauth2.OidcUserInfoMapper} when a
     * request hits {@code GET /oauth2/userinfo} with a valid bearer token that contains
     * {@code scope=openid}. The returned profile is mapped to standard OIDC claims
     * (sub, email, name, preferred_username, locale).</p>
     *
     * <p>TASK-BE-251 Phase 2a — authorization_code flow + /oauth2/userinfo endpoint.</p>
     *
     * @param accountId the account identifier (JWT {@code sub} claim)
     * @return the account profile, or empty if the account does not exist
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    Optional<AccountProfileResult> getAccountProfile(String accountId);

    /**
     * Returns the ACTIVE subscribed product/domain keys for a tenant.
     *
     * <p>TASK-BE-324 (ADR-MONO-019 § 3.3 keystone): called by
     * {@link com.example.auth.infrastructure.oauth2.TenantClaimTokenCustomizer} at
     * {@code authorization_code}/{@code refresh_token} issuance time to populate the
     * signed {@code entitled_domains} claim. Calls
     * {@code GET /internal/tenant-domain-subscriptions?tenantId=<tid>} and extracts
     * {@code items[].domainKey}.</p>
     *
     * <p>Throws {@link com.example.auth.application.exception.AccountServiceUnavailableException}
     * on account-service failure (5xx / circuit-open / timeout / IO) — consistent with the
     * sibling methods. The <b>caller decides fail-soft</b>: the token customizer catches this
     * and omits the claim so token issuance never depends on account-service availability.</p>
     *
     * @param tenantId the tenant whose ACTIVE subscriptions to resolve
     * @return the ACTIVE subscribed domainKeys (possibly empty)
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    List<String> listEntitledDomains(String tenantId);

    /**
     * ADR-MONO-033 S2: roles source for JWT issuance, called by the (future)
     * TenantClaimTokenCustomizer roles leg.
     * GET /internal/tenants/{tid}/accounts/{aid}/roles, extracts roles[].
     * Throws AccountServiceUnavailableException on failure — the caller fail-softs (ADR-033 S5).
     *
     * @param tenantId  the tenant scope
     * @param accountId the account whose roles to resolve
     * @return the role names assigned to the account (possibly empty)
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    List<String> listAccountRoles(String tenantId, String accountId);
}
