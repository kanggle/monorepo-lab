package com.wms.inbound.domain.exception;

/**
 * Base class for all inbound service domain exceptions.
 *
 * <p>Each concrete subclass overrides {@link #errorCode()} to return a
 * machine-readable contract code declared in
 * {@code specs/contracts/http/inbound-service-api.md} §"Error Codes".
 * The {@code GlobalExceptionHandler} calls this method to populate the
 * {@code ApiErrorEnvelope.code} field, enabling API consumers to branch
 * on a stable string rather than an HTTP status alone.
 */
public abstract class InboundDomainException extends RuntimeException {

    protected InboundDomainException(String message) {
        super(message);
    }

    protected InboundDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the contract-defined error code string for this exception.
     * The value must match the §"Error Codes" table in
     * {@code inbound-service-api.md} exactly (case-sensitive).
     */
    public abstract String errorCode();
}
