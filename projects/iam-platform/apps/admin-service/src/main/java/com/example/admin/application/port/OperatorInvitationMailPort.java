package com.example.admin.application.port;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 (owner decision OD-4) — sends the operator-invitation mail through account-service's
 * {@code TASK-MONO-770} sender ({@code POST /internal/notifications/operator-invitation},
 * admin-to-account.md). admin-service has no mail adapter of its own and must not grow one (a third sender copy).
 *
 * <p><b>Never throws for a delivery problem</b> — the answer is a {@link DeliveryStatus}, because a failed mail is
 * not a failed invitation: the row stays and the console offers «다시 보내기» (admin-api.md rule «전달»).
 * <b>No retry</b> — mail is not idempotent; the human-held resend is the retry (admin-to-account.md).
 */
public interface OperatorInvitationMailPort {

    enum DeliveryStatus { SENT, FAILED_TRANSIENT, FAILED_PERMANENT }

    /**
     * @param token the RAW invitation token — it exists only in memory, on its way into the mail body. 🔴 The
     *              record's {@code toString} masks it, so passing the record to a logger cannot leak it.
     */
    record InvitationMail(String to, String token, String tenantId, String inviterDisplayName, Instant expiresAt) {
        @Override
        public String toString() {
            return "InvitationMail[to=<masked>, token=<redacted>, tenantId=" + tenantId
                    + ", expiresAt=" + expiresAt + "]";
        }
    }

    /** Sends one mail. {@code SENT} only on account-service 204; anything it cannot judge is transient. */
    DeliveryStatus send(InvitationMail mail);
}
