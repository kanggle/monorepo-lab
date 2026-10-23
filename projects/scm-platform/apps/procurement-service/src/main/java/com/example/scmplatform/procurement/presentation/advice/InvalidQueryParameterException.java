package com.example.scmplatform.procurement.presentation.advice;

/**
 * A query parameter the contract answers with <b>400</b> {@code VALIDATION_ERROR}
 * (TASK-MONO-810: {@code GET /po/metrics?period=} missing or unknown —
 * {@code platform/contracts/performance-metrics-contract.md} § 4).
 *
 * <p>Deliberately <em>not</em> an {@link IllegalArgumentException}: this service answers a bare
 * {@code IllegalArgumentException} at the controller boundary with <b>422</b>
 * ({@link GlobalExceptionHandler#validationFailureStatus()}), and that published behaviour of the
 * other endpoints must not move. A controller wraps the parse failure in this type instead.
 */
public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String message, Throwable cause) {
        super(message, cause);
    }
}
