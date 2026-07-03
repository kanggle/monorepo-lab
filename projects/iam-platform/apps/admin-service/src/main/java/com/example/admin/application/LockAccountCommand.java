package com.example.admin.application;

/**
 * @param tenantId TASK-BE-467 — the actor's resolved active tenant (via
 *                 {@code QueryTenantScopeGate}); stamped as {@code X-Tenant-Id}
 *                 downstream so the account-service confines the target. {@code "*"}
 *                 (SUPER_ADMIN platform scope) → account-service FAN default (net-zero).
 */
public record LockAccountCommand(
        String accountId,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator,
        String tenantId
) {}
