package com.example.account.application.port;

import com.example.account.application.exception.EmailDeliveryException;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6 · owner decision OD-4) — the operator-invitation mail, sent by the same
 * {@code TASK-MONO-770} machinery as the verification mail ({@link EmailVerificationNotifier}): one generic SMTP
 * adapter selected by {@code iam.mail.enabled=true}, a logging stub outside {@code prod} otherwise, and nothing in
 * {@code prod} with mail off (the context fails fast — the 770 / TASK-BE-236 guarantee).
 *
 * <p>Per {@code rules/traits/regulated.md} R4: the token goes into the link in the mail body and nowhere else —
 * never a log line, never an exception message; the recipient is logged masked.
 */
public interface OperatorInvitationNotifier {

    /**
     * Sends the invitation mail. The implementation renders the link
     * {@code <iam.mail.operator-invitation-link-base-url>?token=...}.
     *
     * @param toEmail            recipient (the invited address)
     * @param token              the raw invitation token (single-use credential)
     * @param companyName        the inviting tenant's display name (never null — the caller falls back to its id)
     * @param inviterDisplayName who invited; {@code null} → the sentence is left out
     * @param expiresAt          until when the link works
     * @throws EmailDeliveryException when the mail did not leave; its {@link EmailDeliveryException.Kind} says
     *         whether a resend can help. Its message carries neither the address nor the token.
     */
    void sendOperatorInvitation(String toEmail, String token, String companyName, String inviterDisplayName,
                                Instant expiresAt);
}
