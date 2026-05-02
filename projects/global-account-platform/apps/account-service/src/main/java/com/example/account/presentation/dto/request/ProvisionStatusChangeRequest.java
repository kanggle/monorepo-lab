package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-BE-231: Request DTO for PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status.
 *
 * <p>TASK-BE-267: {@code operatorId} bounded by the
 * {@code account_status_history.actor_id VARCHAR(36)} column.
 */
public record ProvisionStatusChangeRequest(

        @NotBlank(message = "status is required")
        String status,

        @Size(max = 36, message = "operatorId must be at most 36 characters")
        String operatorId
) {
}
