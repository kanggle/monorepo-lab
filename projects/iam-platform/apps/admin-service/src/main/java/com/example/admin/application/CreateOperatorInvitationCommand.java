package com.example.admin.application;

import java.util.List;

/**
 * TASK-MONO-772 S2 — {@code POST /api/admin/operator-invitations} (admin-api.md § Operator Invitation). The body
 * fields arrive unvalidated on purpose: the use case validates them AFTER the reason header and the
 * Idempotency-Key, in the contract's order (permission → reason → key → body → scope → roles → key reuse →
 * tenant → conflicts).
 */
public record CreateOperatorInvitationCommand(
        OperatorContext actor,
        String reason,
        String idempotencyKey,
        String email,
        String displayName,
        List<String> roles,
        String tenantId
) {}
