package com.wms.master.application.service;

import com.wms.master.domain.exception.ConcurrencyConflictException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Static utility that consolidates the two optimistic-locking helpers that each
 * application service previously carried as private methods. All five master-data
 * aggregates (Warehouse, Zone, Location, Sku, Lot) delegate to this class so
 * that the boilerplate lives in exactly one place.
 *
 * <p>Intentionally non-instantiable; callers in the same package use the methods
 * directly without an import statement.
 */
final class AggregateVersionGuard {

    private AggregateVersionGuard() {}

    /**
     * Asserts that the caller-supplied {@code expected} version equals the
     * {@code actual} version loaded from the store. Throws
     * {@link ConcurrencyConflictException} when they differ (HTTP 409).
     *
     * @param aggregateType human-readable aggregate name used in the error message
     * @param id            aggregate identifier
     * @param expected      version supplied by the command (caller's view)
     * @param actual        version loaded from the persistence store
     */
    public static void requireMatch(String aggregateType, UUID id,
                                    long expected, long actual) {
        if (expected != actual) {
            throw new ConcurrencyConflictException(aggregateType, id.toString(),
                    expected, actual);
        }
    }

    /**
     * Executes {@code saveOperation} and returns its result. If the store signals
     * an optimistic-lock collision via {@link ObjectOptimisticLockingFailureException}
     * the exception is translated to {@link ConcurrencyConflictException} (HTTP 409).
     * Any other {@link RuntimeException} propagates unchanged.
     *
     * @param aggregateType  human-readable aggregate name used in the error message
     * @param id             aggregate identifier
     * @param saveOperation  supplier that performs the actual persistence call
     * @param <T>            aggregate type
     * @return the saved aggregate returned by {@code saveOperation}
     */
    public static <T> T saveWithOptimisticLock(String aggregateType, UUID id,
                                               Supplier<T> saveOperation) {
        try {
            return saveOperation.get();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException(aggregateType, id.toString());
        }
    }
}
