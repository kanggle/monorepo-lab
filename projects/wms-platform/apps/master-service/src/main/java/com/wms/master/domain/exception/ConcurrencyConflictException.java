package com.wms.master.domain.exception;

/**
 * Thrown when the caller's optimistic-lock version disagrees with the stored
 * version at save time. Mapped to HTTP 409 {@code CONFLICT} by the REST layer.
 * Raised either by the application service's pre-save version check or by
 * translating {@code ObjectOptimisticLockingFailureException} from the adapter.
 */
public class ConcurrencyConflictException extends MasterDomainException {

    public ConcurrencyConflictException(String aggregateType, String identifier,
                                        long expectedVersion, long actualVersion) {
        super("CONFLICT",
              "Optimistic lock conflict on " + aggregateType + " " + identifier
                      + " (expected version " + expectedVersion
                      + ", current version " + actualVersion + ")");
    }

    public ConcurrencyConflictException(String aggregateType, String identifier) {
        super("CONFLICT",
              "Optimistic lock conflict on " + aggregateType + " " + identifier);
    }
}
