package com.example.auth.application.result;

/**
 * Result of resolving a social (external IdP) login for the SAS browser flow
 * (TASK-BE-396, ADR-006 option B).
 *
 * <p>Unlike the removed {@code OAuthLoginResult} (the legacy custom-JWT JSON flow
 * retired by TASK-BE-398), this record carries NO tokens. The browser flow does not
 * mint a custom JWT — the social
 * authentication terminates in a SAS-consumed authenticated HTTP session, and
 * the standard SAS tokens are issued later by the {@code /oauth2/token}
 * endpoint after the saved {@code /oauth2/authorize} request resumes.
 *
 * @param accountId     the born-unified account id resolved from a pre-existing
 *                      {@code SocialIdentity} or freshly minted via
 *                      {@code /internal/accounts/social-signup} (ADR-036)
 * @param email         the provider-supplied email (used as the SAS principal name)
 * @param isNewAccount  whether the resolution created a new account
 * @param poolAccount   TASK-BE-617 — the account is a consumer-POOL account: the session principal must
 *                      be a pool principal (details {@code tenant_id = consumer-pool}), exactly the shape
 *                      the form login builds, so the token, the SSO gate and refresh map it onto the
 *                      requesting site by the same rule ({@code AuthorizationSessionTenant}).
 *                      {@code false} → the per-tenant session from before (client tenant).
 */
public record BrowserLoginResolution(
        String accountId,
        String email,
        boolean isNewAccount,
        boolean poolAccount
) {

    /** A per-tenant (site) account — the pre-TASK-BE-617 shape. */
    public BrowserLoginResolution(String accountId, String email, boolean isNewAccount) {
        this(accountId, email, isNewAccount, false);
    }
}
