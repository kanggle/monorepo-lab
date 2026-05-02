package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * TASK-BE-231: Request DTO for POST /internal/tenants/{tenantId}/accounts.
 * Maps to ProvisionAccountCommand.
 *
 * <p>TASK-BE-265: role_name max length aligned with DB (VARCHAR(64)) /
 * {@code AccountRoleName} validator and {@code operatorId} bounded by the
 * {@code granted_by VARCHAR(36)} column.
 */
public record ProvisionAccountRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        @Size(max = 100, message = "displayName must be at most 100 characters")
        String displayName,

        String locale,

        String timezone,

        List<@Size(max = 64, message = "each role must be at most 64 characters") String> roles,

        @Size(max = 36, message = "operatorId must be at most 36 characters")
        String operatorId
) {
}
