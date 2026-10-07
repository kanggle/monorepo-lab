package com.example.account.application.exception;

/**
 * TASK-MONO-770 — {@code POST /api/accounts/signup/resend-verification-email} could not send the mail.
 *
 * <p>Before this ticket the failure was swallowed and the endpoint answered 204 — the screen said «sent» for a
 * mail that never left. Once verification became the condition for a company role (ADR-MONO-080 D3) that lie
 * reads as «I was not given the role» (ADR-MONO-080 § 새로 생기는 위험), so the failure is now an answer:
 * {@link EmailDeliveryException.Kind#TRANSIENT} → 503 {@code VERIFICATION_EMAIL_SEND_FAILED},
 * {@link EmailDeliveryException.Kind#PERMANENT} → 422 {@code VERIFICATION_EMAIL_UNDELIVERABLE}.
 */
public class VerificationEmailSendFailedException extends RuntimeException {

    private final EmailDeliveryException.Kind kind;

    public VerificationEmailSendFailedException(EmailDeliveryException.Kind kind) {
        super(kind == EmailDeliveryException.Kind.PERMANENT
                ? "The mail server does not accept this address; the verification email was not sent"
                : "The verification email could not be sent; try again shortly");
        this.kind = kind;
    }

    public EmailDeliveryException.Kind getKind() {
        return kind;
    }
}
