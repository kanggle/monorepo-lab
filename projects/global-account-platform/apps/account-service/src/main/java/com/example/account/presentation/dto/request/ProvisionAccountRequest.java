package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * TASK-BE-231: Request DTO for POST /internal/tenants/{tenantId}/accounts.
 * Maps to ProvisionAccountCommand.
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

        List<@Size(max = 50, message = "each role must be at most 50 characters") String> roles,

        String operatorId
) {
}
