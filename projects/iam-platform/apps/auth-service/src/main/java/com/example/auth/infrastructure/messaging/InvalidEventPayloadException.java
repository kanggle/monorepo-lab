package com.example.auth.infrastructure.messaging;

/**
 * TASK-BE-601 — an inbound event that cannot be processed as sent (unparseable JSON, a
 * missing or malformed required field). Retrying cannot change the bytes, so the record is
 * routed to the DLQ without retrying.
 */
public class InvalidEventPayloadException extends RuntimeException {

    public InvalidEventPayloadException(String message) {
        super(message);
    }

    public InvalidEventPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
