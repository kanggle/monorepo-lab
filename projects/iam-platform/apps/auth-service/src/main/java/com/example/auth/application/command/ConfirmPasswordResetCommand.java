package com.example.auth.application.command;

/**
 * Command for {@code POST /api/auth/password-reset/confirm} (TASK-BE-109).
 *
 * <p>Both fields are sensitive plaintext and must never be logged (R4 —
 * rules/traits/regulated.md). The {@code token} is opaque to the application
 * layer and is resolved against the Redis-backed
 * {@link com.example.auth.domain.repository.PasswordResetTokenStore}.</p>
 */
public record ConfirmPasswordResetCommand(
        String token,
        String newPassword
) {
}
