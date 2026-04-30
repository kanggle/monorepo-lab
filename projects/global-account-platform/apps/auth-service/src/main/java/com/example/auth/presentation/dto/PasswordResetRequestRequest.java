package com.example.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/password-reset/request} (TASK-BE-108).
 *
 * <p>Email format is validated at the controller boundary — invalid emails
 * yield 400 {@code VALIDATION_ERROR}, mirroring the login DTO. Existence of
 * the address is intentionally <strong>not</strong> validated here so the
 * endpoint cannot be used to enumerate accounts.</p>
 */
public record PasswordResetRequestRequest(
        @NotBlank
        @Email
        String email
) {
}
