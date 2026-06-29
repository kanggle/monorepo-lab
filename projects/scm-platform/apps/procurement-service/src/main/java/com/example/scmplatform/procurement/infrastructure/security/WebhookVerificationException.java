package com.example.scmplatform.procurement.infrastructure.security;

/**
 * Thrown by {@link WebhookSignatureVerifier} when an inbound webhook fails
 * HMAC / timestamp / replay verification.
 *
 * <p>{@link #getCode()} carries the machine-readable reason — one of
 * {@code WEBHOOK_SIGNATURE_INVALID}, {@code WEBHOOK_TIMESTAMP_INVALID}, or
 * {@code WEBHOOK_REPLAY_DETECTED}. {@link WebhookSignatureFilter} surfaces it
 * as a {@code 401 UNAUTHORIZED} envelope with the code as the message.
 */
public class WebhookVerificationException extends RuntimeException {

    private final String code;

    public WebhookVerificationException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
