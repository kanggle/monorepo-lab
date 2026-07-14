package com.example.security.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Owned by security-service (TASK-MONO-406 — moved out of {@code libs/java-messaging};
 * see {@link ProcessedEventJpaEntity}).
 *
 * <p>Scanned by {@code JpaConfig}'s
 * {@code @EnableJpaRepositories(basePackages = "com.example.security.infrastructure.persistence")}.
 * That config does not set {@code enableDefaultTransactions = false} as the library's did.
 * Verified, not assumed: the only write is {@code RecordLoginHistoryUseCase.execute},
 * which is {@code @Transactional}, so the dedupe row and the login-history row still
 * commit atomically. The one non-transactional read ({@code EventDedupService}'s advisory
 * Redis-miss fallback) now runs inside a read-only transaction instead of none — same
 * result, and the MySQL UNIQUE constraint remains the authority either way.
 *
 * <p>The library's {@code deleteByProcessedAtBefore} retention query is not carried over:
 * security-service never called it. Carrying it would ship a method with no caller.
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {

    boolean existsByEventId(String eventId);
}
