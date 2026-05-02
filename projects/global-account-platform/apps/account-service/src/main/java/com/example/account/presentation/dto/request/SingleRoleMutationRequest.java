package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-BE-255: Request DTO for the single-role add/remove endpoints
 * ({@code PATCH .../roles:add}, {@code PATCH .../roles:remove}).
 *
 * <p>TASK-BE-265: {@code operatorId} length is bounded by the
 * {@code granted_by VARCHAR(36)} column. Without this guard the DB raises
 * a Data truncation error and surfaces as 500 instead of 400.
 */
public record SingleRoleMutationRequest(

        @NotBlank(message = "roleName must not be blank")
        @Size(max = 64, message = "roleName must be at most 64 characters")
        String roleName,

        @Size(max = 36, message = "operatorId must be at most 36 characters")
        String operatorId
) {
}
