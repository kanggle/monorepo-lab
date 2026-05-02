package com.example.account.application.exception;

/**
 * TASK-BE-257: Thrown when the {@code items} array in a bulk provisioning request
 * exceeds the maximum allowed size of 1 000.
 * Maps to 400 {@code BULK_LIMIT_EXCEEDED}.
 */
public class BulkLimitExceededException extends RuntimeException {

    private final int requested;
    private final int limit;

    public BulkLimitExceededException(int requested, int limit) {
        super("Bulk limit exceeded: requested " + requested + " items, maximum is " + limit);
        this.requested = requested;
        this.limit = limit;
    }

    public int getRequested() {
        return requested;
    }

    public int getLimit() {
        return limit;
    }
}
