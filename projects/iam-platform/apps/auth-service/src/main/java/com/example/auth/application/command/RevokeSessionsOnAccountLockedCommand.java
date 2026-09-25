package com.example.auth.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TASK-BE-601 — one {@code account.locked} event, as the use case needs it.
 *
 * @param eventId    the event's own id — the dedupe key
 * @param accountId  the locked account
 * @param tenantId   the account's tenant as account-service published it (required by the
 *                   envelope rule; carried for the log line, not used to confine the revoke —
 *                   see {@code RevokeSessionsOnAccountLockedUseCase})
 * @param reasonCode {@code ADMIN_LOCK | AUTO_DETECT | PASSWORD_FAILURE_THRESHOLD}, or null
 * @param lockedAt   when account-service locked the account, or null when absent
 */
public record RevokeSessionsOnAccountLockedCommand(
        UUID eventId,
        String accountId,
        String tenantId,
        String reasonCode,
        Instant lockedAt
) {
    public RevokeSessionsOnAccountLockedCommand {
        Objects.requireNonNull(eventId, "eventId");
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }
}
