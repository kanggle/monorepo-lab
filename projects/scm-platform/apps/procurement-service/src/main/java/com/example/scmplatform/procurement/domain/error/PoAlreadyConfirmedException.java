package com.example.scmplatform.procurement.domain.error;

/** Mapped to HTTP 422 {@code PO_ALREADY_CONFIRMED}. */
public class PoAlreadyConfirmedException extends RuntimeException {
    public PoAlreadyConfirmedException(String message) {
        super(message);
    }
}
