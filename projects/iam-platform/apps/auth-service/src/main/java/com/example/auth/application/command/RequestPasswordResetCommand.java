package com.example.auth.application.command;

/**
 * Command for {@code POST /api/auth/password-reset/request} (TASK-BE-108).
 *
 * <p>The use case normalizes the email (lower-case + trim) before looking up
 * the credential — see {@code com.example.auth.domain.credentials.Credential#normalizeEmail}.
 * Callers may pass the raw client input here.</p>
 */
public record RequestPasswordResetCommand(
        String email
) {
}
