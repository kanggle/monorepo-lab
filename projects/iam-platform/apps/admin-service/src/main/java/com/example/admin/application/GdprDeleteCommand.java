package com.example.admin.application;

/**
 * @param tenantId TASK-BE-467 — the actor's resolved active tenant (stamped
 *                 downstream as {@code X-Tenant-Id}; {@code "*"} → FAN default).
 */
public record GdprDeleteCommand(
        String accountId,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator,
        String tenantId
) {}
