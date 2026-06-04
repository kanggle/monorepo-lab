package com.example.erp.readmodel.adapter.inbound.messaging;

/**
 * Signals an envelope validation failure — the consumer routes the message to
 * the DLT without retry (architecture.md Failure Mode 2: invalid envelope,
 * null {@code eventId}/{@code payload}/{@code changeKind} → immediate DLT).
 */
public class InvalidEnvelopeException extends RuntimeException {

    public InvalidEnvelopeException(String message) {
        super(message);
    }
}
