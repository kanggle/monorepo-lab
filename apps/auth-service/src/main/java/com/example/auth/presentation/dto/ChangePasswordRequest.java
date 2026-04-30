package com.example.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PATCH /api/auth/password}.
 *
 * <p>Both fields carry plaintext passwords on this in-memory boundary only —
 * they must never be logged (R4 — rules/traits/regulated.md). The full policy
 * (length, complexity, email-equality) is enforced inside the use case via
 * {@code PasswordPolicy}; the only validation here is presence.</p>
 */
public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        String newPassword
) {
}
