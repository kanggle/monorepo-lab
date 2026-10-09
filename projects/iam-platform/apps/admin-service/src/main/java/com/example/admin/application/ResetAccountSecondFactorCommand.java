package com.example.admin.application;

/**
 * TASK-MONO-771 S6 — {@code POST /api/admin/accounts/{accountId}/2fa/reset}.
 *
 * @param reason   the detailed reason from the body (the audit row's {@code reason}); the
 *                 {@code X-Operator-Reason} header was already required by the controller
 * @param ticketId optional internal ticket reference (body)
 */
public record ResetAccountSecondFactorCommand(
        String accountId,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator
) {}
