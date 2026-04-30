package com.example.auth.application.command;

/**
 * Command for {@code PATCH /api/auth/password} — change the authenticated
 * caller's password after verifying the current one.
 *
 * <p>Both {@code currentPassword} and {@code newPassword} are plaintext on
 * this in-memory boundary only and must never be logged (R4 —
 * rules/traits/regulated.md).</p>
 */
public record ChangePasswordCommand(
        String accountId,
        String currentPassword,
        String newPassword
) {
}
