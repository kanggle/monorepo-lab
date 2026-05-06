package com.example.scmplatform.procurement.domain.error;

/** Mapped to HTTP 404 {@code PO_NOT_FOUND}. */
public class PoNotFoundException extends RuntimeException {
    public PoNotFoundException(String message) {
        super(message);
    }
}
