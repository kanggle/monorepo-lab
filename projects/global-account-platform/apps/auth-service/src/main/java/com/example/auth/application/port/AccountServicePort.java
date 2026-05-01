package com.example.auth.application.port;

import com.example.auth.application.result.AccountProfileResult;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.SocialSignupResult;

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
}
