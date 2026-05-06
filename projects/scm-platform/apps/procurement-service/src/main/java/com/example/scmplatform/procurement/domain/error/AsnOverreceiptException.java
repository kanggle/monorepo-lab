package com.example.scmplatform.procurement.domain.error;

/** Mapped to HTTP 422 {@code ASN_OVERRECEIPT}. */
public class AsnOverreceiptException extends RuntimeException {
    public AsnOverreceiptException(String message) {
        super(message);
    }
}
