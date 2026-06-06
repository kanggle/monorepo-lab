package com.example.admin.application;

import java.time.Instant;

/**
 * TASK-BE-249: {@code tenantId} added — the tenant scope the caller is querying.
 * When {@code null} the use-case defaults to the operator's own tenantId.
 */
public record QueryAuditCommand(
        String accountId,
        String actionCode,
        Instant from,
        Instant to,
        String source,
        int page,
        int size,
        String idempotencyKey,
        String reason,
        OperatorContext operator,
        /** The tenant being queried. Null → operator's own tenant. "*" → cross-tenant (SUPER_ADMIN only). */
        String tenantId
) {
    /** Backward-compat constructor for call sites that predate TASK-BE-249. */
    public QueryAuditCommand(String accountId, String actionCode, Instant from, Instant to,
                             String source, int page, int size, String idempotencyKey,
                             String reason, OperatorContext operator) {
        this(accountId, actionCode, from, to, source, page, size, idempotencyKey, reason, operator, null);
    }
}
