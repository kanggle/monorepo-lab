package com.example.auth.application.result;

/**
 * Result of resolving a social (external IdP) login for the SAS browser flow
 * (TASK-BE-396, ADR-006 option B).
 *
 * <p>Unlike {@link OAuthLoginResult} (the legacy custom-JWT JSON flow), this
 * record carries NO tokens. The browser flow does not mint a JWT — the social
 * authentication terminates in a SAS-consumed authenticated HTTP session, and
 * the standard SAS tokens are issued later by the {@code /oauth2/token}
 * endpoint after the saved {@code /oauth2/authorize} request resumes.
 *
 * @param accountId     the born-unified account id resolved from a pre-existing
 *                      {@code SocialIdentity} or freshly minted via
 *                      {@code /internal/accounts/social-signup} (ADR-036)
 * @param email         the provider-supplied email (used as the SAS principal name)
 * @param isNewAccount  whether the resolution created a new account
 */
public record BrowserLoginResolution(
        String accountId,
        String email,
        boolean isNewAccount
) {
}
