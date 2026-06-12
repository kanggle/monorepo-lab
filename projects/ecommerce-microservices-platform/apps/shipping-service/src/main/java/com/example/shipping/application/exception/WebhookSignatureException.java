package com.example.shipping.application.exception;

/**
 * Raised when a carrier webhook request fails signature verification (TASK-BE-294):
 * missing/mismatched {@code X-Carrier-Signature}, or the shared secret is not configured
 * (fail-closed — an unconfigured secret rejects all webhooks). Mapped to HTTP 401.
 */
public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException(String message) {
        super(message);
    }
}
