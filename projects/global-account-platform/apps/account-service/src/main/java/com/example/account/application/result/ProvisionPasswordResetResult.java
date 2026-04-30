package com.example.account.application.result;

import java.time.Instant;

/**
 * TASK-BE-231: Result of an operator-initiated password-reset token issuance.
 * No sensitive fields are included in the response.
 */
public record ProvisionPasswordResetResult(
        String accountId,
        String tenantId,
        Instant resetTokenIssuedAt,
        String message
) {
}
