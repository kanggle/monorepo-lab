package com.example.fanplatform.notification.application.consumer;

/**
 * Raised when an envelope cannot be parsed or is missing a required field. The
 * consumer's {@code DefaultErrorHandler} treats this as <b>non-retryable</b> →
 * the record is routed straight to {@code <topic>.dlq} (no pointless 3× retry of
 * a payload that will never parse).
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }
}
