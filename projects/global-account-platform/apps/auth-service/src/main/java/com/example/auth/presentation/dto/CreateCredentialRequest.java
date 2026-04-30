package com.example.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /internal/auth/credentials} (internal only —
 * invoked by account-service during signup, never exposed through the gateway).
 *
 * <p>TASK-BE-229: {@code tenantId} is now included. When absent, defaults to "fan-platform".</p>
 */
public record CreateCredentialRequest(
        @NotBlank(message = "accountId is required")
        @Size(max = 36, message = "accountId must not exceed 36 characters")
        String accountId,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        /** Optional. Tenant slug. Defaults to "fan-platform" when null. */
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$",
                 message = "tenantId must be a valid tenant slug")
        String tenantId
) {
}
