package com.example.admin.application;

import java.util.List;

/**
 * @param tenantId TASK-BE-467 — the actor's resolved active tenant; propagated to
 *                 every per-row {@link LockAccountCommand} so each row is confined
 *                 (cross-tenant row → that row's {@code ACCOUNT_NOT_FOUND} outcome,
 *                 batch still 200). {@code "*"} → account-service FAN default.
 */
public record BulkLockAccountCommand(
        List<String> accountIds,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator,
        String tenantId
) {}
