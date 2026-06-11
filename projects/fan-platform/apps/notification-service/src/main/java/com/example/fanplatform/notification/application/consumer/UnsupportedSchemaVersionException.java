package com.example.fanplatform.notification.application.consumer;

/**
 * Raised when an envelope's {@code schemaVersion} is not supported by this
 * consumer (architecture.md § Schema versioning). Treated as <b>non-retryable</b>
 * by the {@code DefaultErrorHandler} → routed straight to {@code <topic>.dlq}
 * (never silently dropped).
 */
public class UnsupportedSchemaVersionException extends RuntimeException {

    public UnsupportedSchemaVersionException(int schemaVersion, String eventType) {
        super("Unsupported schemaVersion " + schemaVersion + " for event " + eventType);
    }
}
