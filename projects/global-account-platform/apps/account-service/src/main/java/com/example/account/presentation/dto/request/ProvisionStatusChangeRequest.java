package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * TASK-BE-231: Request DTO for PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status.
 */
public record ProvisionStatusChangeRequest(

        @NotBlank(message = "status is required")
        String status,

        String operatorId
) {
}
