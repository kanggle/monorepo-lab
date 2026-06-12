package com.example.finance.ledger.messaging;

/**
 * Raised when a transaction envelope cannot be mapped to a domain command
 * (unparseable JSON, missing dedupe key / required field, unknown transaction
 * type). The consumer routes it straight to the DLT without retry (no poison
 * loop — architecture.md § Failure Modes 3).
 */
public class InvalidEnvelopeException extends RuntimeException {
    public InvalidEnvelopeException(String message) {
        super(message);
    }
}
