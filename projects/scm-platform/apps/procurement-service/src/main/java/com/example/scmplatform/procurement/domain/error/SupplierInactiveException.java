package com.example.scmplatform.procurement.domain.error;

/** Mapped to HTTP 422 {@code SUPPLIER_INACTIVE}. */
public class SupplierInactiveException extends RuntimeException {
    public SupplierInactiveException(String message) {
        super(message);
    }
}
