package com.wms.outbound.domain.exception;

/**
 * Base type for outbound-service domain exceptions.
 *
 * <p>Each concrete subtype carries the contract-defined error code
 * (see {@code specs/contracts/http/outbound-service-api.md} § Error Codes).
 * The {@code GlobalExceptionHandler} reads {@link #errorCode()} to populate
 * the {@code ApiErrorEnvelope.code} field.
 */
public abstract class OutboundDomainException extends RuntimeException {

    protected OutboundDomainException(String message) {
        super(message);
    }

    protected OutboundDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Stable error code as declared in the HTTP contract.
     */
    public abstract String errorCode();
}
