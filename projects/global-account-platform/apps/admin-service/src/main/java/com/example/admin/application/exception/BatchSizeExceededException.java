package com.example.admin.application.exception;

/**
 * Thrown when a bulk admin command receives more items than the per-request
 * cap permits. Surfaces as {@code 422 BATCH_SIZE_EXCEEDED} via
 * {@code AdminExceptionHandler}.
 */
public class BatchSizeExceededException extends RuntimeException {
    public BatchSizeExceededException(String message) {
        super(message);
    }
}
