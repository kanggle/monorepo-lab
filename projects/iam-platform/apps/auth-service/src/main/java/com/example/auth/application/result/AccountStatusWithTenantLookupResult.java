package com.example.auth.application.result;

/**
 * TASK-BE-602 — result of account-service's
 * {@code GET /internal/accounts/{id}/status-with-tenant}: the account's status together with the
 * tenant its {@code accounts} row actually lives in.
 *
 * <p>The social-login callback uses {@code tenantId} as the account's tenant (for the login events)
 * instead of the initiating client's tenant or the social-identity row's tenant, both of which are
 * wrong for accounts born before TASK-BE-507. Both fields are never blank — the adapter treats a
 * 200 without either as a failed lookup.
 */
public record AccountStatusWithTenantLookupResult(
        String accountId,
        String tenantId,
        String accountStatus
) {
}
