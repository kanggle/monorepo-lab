package com.example.scmplatform.demandplanning.adapter.inbound.messaging;

/**
 * Marker exception for non-retryable consumer failures (e.g. malformed envelope,
 * unmapped SKU). Causes the message to be routed immediately to the DLT without
 * exhausting the retry attempts.
 */
public class NonRetryableConsumerException extends RuntimeException {

    public NonRetryableConsumerException(String message) {
        super(message);
    }

    public NonRetryableConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}
