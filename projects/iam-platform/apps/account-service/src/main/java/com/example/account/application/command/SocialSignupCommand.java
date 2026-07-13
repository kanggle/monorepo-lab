package com.example.account.application.command;

/**
 * TASK-BE-507: {@code tenantId} is the tenant the account is born into. auth-service already
 * resolves it from the initiating OIDC client ({@code SavedRequestTenantResolver}) for the
 * social-identity row and the token — it now threads the same value here, so the account row
 * stops contradicting the token the same login will be issued.
 */
public record SocialSignupCommand(
        String email,
        String provider,
        String providerUserId,
        String displayName,
        String tenantId
) {
}
