package com.example.account.application.exception;

/**
 * TASK-MONO-772 S2 — {@code POST /internal/notifications/operator-invitation} could not send the mail
 * (admin-to-account.md): {@link EmailDeliveryException.Kind#TRANSIENT} → 503 {@code INVITATION_EMAIL_SEND_FAILED},
 * {@link EmailDeliveryException.Kind#PERMANENT} → 422 {@code INVITATION_EMAIL_UNDELIVERABLE}. admin-service turns
 * the two into the invitation's {@code delivery.status} ({@code FAILED_TRANSIENT} / {@code FAILED_PERMANENT}).
 * Fixed messages — neither the address nor the token (R4).
 */
public class InvitationEmailSendFailedException extends RuntimeException {

    private final EmailDeliveryException.Kind kind;

    public InvitationEmailSendFailedException(EmailDeliveryException.Kind kind) {
        super(kind == EmailDeliveryException.Kind.PERMANENT
                ? "The mail server does not accept this address; the invitation email was not sent"
                : "The invitation email could not be sent; try again shortly");
        this.kind = kind;
    }

    public EmailDeliveryException.Kind getKind() {
        return kind;
    }
}
