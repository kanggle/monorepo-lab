package com.example.account.application.command;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 — one operator-invitation mail (admin-to-account.md § notifications/operator-invitation).
 * 🔴 {@code token} is the raw invitation token; {@code toString} redacts it and masks the recipient so no log line
 * that prints the command can leak either.
 */
public record SendOperatorInvitationMailCommand(
        String to,
        String token,
        String tenantId,
        String inviterDisplayName,
        Instant expiresAt
) {
    @Override
    public String toString() {
        return "SendOperatorInvitationMailCommand[to=<masked>, token=<redacted>, tenantId=" + tenantId
                + ", expiresAt=" + expiresAt + "]";
    }
}
