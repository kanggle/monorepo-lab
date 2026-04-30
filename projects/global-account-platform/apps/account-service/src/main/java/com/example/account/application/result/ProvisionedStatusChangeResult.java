package com.example.account.application.result;

import java.time.Instant;

/**
 * TASK-BE-231: Result of a tenant-scoped account status change via the internal provisioning API.
 * Includes tenantId to distinguish from the generic {@link StatusChangeResult}.
 */
public record ProvisionedStatusChangeResult(
        String accountId,
        String tenantId,
        String previousStatus,
        String currentStatus,
        Instant changedAt
) {
}
