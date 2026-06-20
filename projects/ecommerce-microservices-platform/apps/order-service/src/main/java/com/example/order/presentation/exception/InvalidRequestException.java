package com.example.order.presentation.exception;

/**
 * Raised when an internal-endpoint request body carries an out-of-range value
 * (TASK-BE-412: {@code olderThanMinutes < 1} or {@code limit} outside {@code 1..1000}).
 * Mapped to {@code 400 INVALID_REQUEST} by the {@code GlobalExceptionHandler}, matching
 * the {@code order-confirm-paid-stale.md} error contract.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
