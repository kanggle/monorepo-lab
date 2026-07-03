package com.example.admin.application;

/**
 * @param tenantId TASK-BE-467 — the actor's resolved active tenant, stamped as
 *                 {@code X-Tenant-Id} on the auth-service force-logout call
 *                 (defense-in-depth propagation; the refresh_tokens tenant_id
 *                 enforcement at auth-service is the acknowledged separate point).
 *                 {@code "*"} = SUPER_ADMIN platform scope.
 */
public record RevokeSessionCommand(
        String accountId,
        String reason,
        String idempotencyKey,
        OperatorContext operator,
        String tenantId
) {}
