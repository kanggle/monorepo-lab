package com.example.account.presentation.dto.request;

/**
 * TASK-BE-231: Request DTO for POST /internal/tenants/{tenantId}/accounts/{accountId}/password-reset.
 * The body is optional; only operatorId is accepted.
 */
public record ProvisionPasswordResetRequest(
        String operatorId
) {
}
