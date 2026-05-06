package com.example.scmplatform.procurement.domain.error;

/** Mapped to HTTP 404 {@code SUPPLIER_NOT_FOUND}. */
public class SupplierNotFoundException extends RuntimeException {
    public SupplierNotFoundException(String message) {
        super(message);
    }
}
