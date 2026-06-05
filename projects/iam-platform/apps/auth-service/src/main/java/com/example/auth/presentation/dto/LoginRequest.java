package com.example.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Login request DTO.
 *
 * <p>TASK-BE-229: {@code tenantId} is an optional field. When absent, the system
 * auto-detects the tenant; if the email exists in multiple tenants,
 * {@code LOGIN_TENANT_AMBIGUOUS} 400 is returned.
 */
public record LoginRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8)
        String password,

        /** Optional. Tenant slug (e.g. "fan-platform", "wms"). */
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$",
                 message = "tenantId must be a valid tenant slug (lowercase letters, digits, hyphens)")
        String tenantId
) {
}
