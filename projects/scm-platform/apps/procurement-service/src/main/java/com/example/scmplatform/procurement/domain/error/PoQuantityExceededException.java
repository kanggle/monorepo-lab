package com.example.scmplatform.procurement.domain.error;

/** Mapped to HTTP 422 {@code PO_QUANTITY_EXCEEDED}. */
public class PoQuantityExceededException extends RuntimeException {
    public PoQuantityExceededException(String message) {
        super(message);
    }
}
