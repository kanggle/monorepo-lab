package com.example.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/password-reset/confirm} (TASK-BE-109).
 *
 * <p>Both fields are required. The {@code token} is opaque (the application
 * layer does not interpret it) and the {@code newPassword} is later validated
 * by {@code PasswordPolicy} inside the use case — controller-side validation
 * deliberately stops at {@code @NotBlank} so policy violations surface as
 * {@code PASSWORD_POLICY_VIOLATION} (400) rather than {@code VALIDATION_ERROR}.</p>
 */
public record PasswordResetConfirmRequest(
        @NotBlank
        String token,

        @NotBlank
        String newPassword
) {
}
