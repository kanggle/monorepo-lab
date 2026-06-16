package com.example.security.domain.repository;

import com.example.security.domain.history.AccountLockHistory;

/**
 * Domain repository port for the append-only {@code account_lock_history} log.
 *
 * <p>Mirrors {@link LoginHistoryRepository}: the {@code consumer/} layer records
 * history through an application use case that depends on this port, never on the
 * {@code infrastructure/persistence} JPA types directly (declared dependency rule
 * {@code consumer → application → domain}).
 */
public interface AccountLockHistoryRepository {

    /**
     * Append an immutable {@code account_lock_history} row.
     *
     * <p>Implementations propagate a Spring
     * {@link org.springframework.dao.DataIntegrityViolationException} on a duplicate
     * {@code event_id} so the use case can treat the Kafka at-least-once replay as
     * idempotent success.
     */
    void save(AccountLockHistory entry);
}
