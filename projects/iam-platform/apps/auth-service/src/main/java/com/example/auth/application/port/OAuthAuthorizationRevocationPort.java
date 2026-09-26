package com.example.auth.application.port;

/**
 * TASK-BE-601 — revokes the refresh tokens an account holds in the OIDC authorization
 * store (the Spring Authorization Server {@code oauth2_authorization} table and its
 * {@code refresh_tokens} mirror rows).
 *
 * <p>Why a separate port and not {@code RefreshTokenRepository#revokeAllByAccountId}:
 * every browser session today is a SAS session, and SAS records the authorization under
 * its principal NAME — the login email, not the account id
 * ({@code CredentialAuthenticationProvider} / {@code SocialLoginBrowserController} build
 * the principal as {@code UsernamePasswordAuthenticationToken(email, …)}). A revoke keyed on
 * the account id therefore never reaches the authorization, and the SAS
 * {@code refresh_token} grant keeps accepting it.
 *
 * <p>TASK-BE-603: the {@code refresh_tokens} mirror rows are now keyed by the account UUID
 * (they used to copy the principal name, i.e. the email), so {@code revokeAllByAccountId}
 * reaches new SAS mirror rows. Until TASK-BE-604 that did NOT refuse the next refresh (the
 * custom provider's rejection fell through to SAS's built-in refresh provider, which checks the
 * authorization only — TASK-BE-603 § AC-2); BE-604 removed that provider, so it does now. This
 * port is still what reaches every SAS session of an account, including those whose mirror
 * row predates BE-603 and is keyed by the email: it closes the authorization itself.
 *
 * <p>Implementations MUST confine the revoke to the given account: an email is not unique
 * across tenants (the same address may own an account in {@code fan-platform} and in
 * {@code ecommerce}), so matching on the principal name alone would log out a different
 * account.
 */
public interface OAuthAuthorizationRevocationPort {

    /**
     * Invalidates every still-active refresh token the account holds in the authorization
     * store, and marks the matching {@code refresh_tokens} mirror rows revoked.
     *
     * @param accountId the account whose sessions are revoked
     * @return how many authorizations had an active refresh token that is now invalidated
     *         (0 when the account holds none — already-invalidated tokens are not counted,
     *         so a repeated call returns 0)
     */
    int revokeActiveRefreshTokens(String accountId);
}
