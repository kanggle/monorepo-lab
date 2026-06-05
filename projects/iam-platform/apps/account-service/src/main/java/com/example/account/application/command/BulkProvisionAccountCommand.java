package com.example.account.application.command;

import java.util.List;

/**
 * TASK-BE-257: Command for the bulk provisioning API.
 *
 * <p>Carries all per-row fields needed to create accounts in a single batch request.
 * The {@code operatorId} is a request-level attribution (one caller for all rows).
 */
public record BulkProvisionAccountCommand(
        String tenantId,
        List<Item> items,
        String operatorId
) {

    /**
     * Per-row item in the bulk provisioning request.
     *
     * @param externalId  caller-side dedup key (optional, no server-side uniqueness constraint)
     * @param email       target email address
     * @param phone       optional phone number
     * @param displayName optional display name (max 100 chars)
     * @param roles       optional role names; each must match {@code ^[A-Z][A-Z0-9_]*$}
     * @param status      initial account status; defaults to ACTIVE if null
     */
    public record Item(
            String externalId,
            String email,
            String phone,
            String displayName,
            List<String> roles,
            String status
    ) {}
}
