package com.example.finance.account.presentation.advice;

/**
 * A query parameter the contract answers with <b>400</b> {@code VALIDATION_ERROR}
 * (TASK-MONO-812: {@code GET /api/finance/accounts/metrics?period=} missing or unknown —
 * {@code platform/contracts/performance-metrics-contract.md} § 4).
 *
 * <p>Deliberately <em>not</em> an {@link IllegalArgumentException}: this service answers a bare
 * {@code IllegalArgumentException} at the controller boundary with <b>422</b>
 * {@code AMOUNT_INVALID} ({@link GlobalExceptionHandler#handleIllegalArgument}), and that
 * published behaviour of the other endpoints must not move. The controller wraps the parse
 * failure in this type instead (same shape as scm procurement-service, TASK-MONO-810).
 */
public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String message, Throwable cause) {
        super(message, cause);
    }
}
