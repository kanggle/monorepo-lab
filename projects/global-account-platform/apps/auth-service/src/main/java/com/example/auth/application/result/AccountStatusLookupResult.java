package com.example.auth.application.result;

/**
 * Result of a status-only lookup against account-service's
 * {@code GET /internal/accounts/{id}/status} endpoint.
 *
 * <p>Introduced by TASK-BE-063 to replace the previous credential-lookup result
 * on the login hot path — credentials are now owned locally by auth-service, so
 * the remote call carries status data only.</p>
 */
public record AccountStatusLookupResult(
        String accountId,
        String accountStatus
) {
}
