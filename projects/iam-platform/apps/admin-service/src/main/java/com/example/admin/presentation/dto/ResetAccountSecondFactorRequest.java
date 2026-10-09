package com.example.admin.presentation.dto;

/**
 * TASK-MONO-771 S6 — body of {@code POST /api/admin/accounts/{accountId}/2fa/reset} (admin-api.md).
 *
 * @param reason   required — the detailed reason (identity-verification basis); the audit row's {@code reason}
 * @param ticketId optional internal ticket reference
 */
public record ResetAccountSecondFactorRequest(
        String reason,
        String ticketId
) {}
