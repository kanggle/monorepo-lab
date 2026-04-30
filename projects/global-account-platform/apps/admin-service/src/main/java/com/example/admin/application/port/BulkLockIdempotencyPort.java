package com.example.admin.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Application-side port for the bulk-lock idempotency record store. Allows
 * {@code BulkLockAccountUseCase} to remain free of direct dependencies on
 * {@code infrastructure/persistence/*Jpa*} types, per architecture.md Allowed
 * Dependencies.
 */
public interface BulkLockIdempotencyPort {

    /**
     * Look up a stored idempotency record for the given operator + key.
     */
    Optional<Record> find(Long operatorId, String idempotencyKey);

    /**
     * Persist a new idempotency record. Implementations MUST surface the
     * underlying PK collision as a {@link DuplicateKeyException} so that
     * callers can perform the find-or-save race resolution deterministically.
     */
    void save(Long operatorId, String idempotencyKey, String requestHash,
              String responseBody, Instant createdAt) throws DuplicateKeyException;

    /**
     * Signals that a {@link #save} attempt collided with an existing row for
     * the same {@code (operatorId, idempotencyKey)} — a concurrent first
     * request has already committed.
     */
    class DuplicateKeyException extends RuntimeException {
        public DuplicateKeyException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Projection of the stored idempotency row. Keeps the application layer
     * independent of the JPA entity shape.
     */
    record Record(Long operatorId, String idempotencyKey,
                  String requestHash, String responseBody, Instant createdAt) {}
}
