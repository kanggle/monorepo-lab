package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-BE-255: Request DTO for the single-role add/remove endpoints
 * ({@code PATCH .../roles:add}, {@code PATCH .../roles:remove}).
 */
public record SingleRoleMutationRequest(

        @NotBlank(message = "roleName must not be blank")
        @Size(max = 64, message = "roleName must be at most 64 characters")
        String roleName,

        String operatorId
) {
}
