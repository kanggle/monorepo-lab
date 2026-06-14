package com.example.account.presentation.dto.response;

import java.util.List;

/**
 * TASK-BE-368 (ADR-MONO-033 S2 / ADR-032 step 2.1): Response DTO for the
 * GET /internal/tenants/{tenantId}/accounts/{accountId}/roles endpoint.
 *
 * <p>Carries the full set of role names assigned to the account within the tenant.
 * An empty {@code roles} list is a valid response (enumeration-safe).
 */
public record AccountRolesResponse(
        String accountId,
        String tenantId,
        List<String> roles
) {
    /**
     * Factory for consistent construction at the controller layer.
     */
    public static AccountRolesResponse of(String accountId, String tenantId, List<String> roles) {
        return new AccountRolesResponse(accountId, tenantId, roles);
    }
}
