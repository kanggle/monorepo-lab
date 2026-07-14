package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Owned by account-service (TASK-MONO-406 — moved out of {@code libs/java-messaging};
 * see {@link ProcessedEventJpaEntity}).
 *
 * <p>Scanned by {@code JpaConfig}'s
 * {@code @EnableJpaRepositories(basePackages = "com.example.account.infrastructure.persistence")}.
 * That config does not set {@code enableDefaultTransactions = false} as the library's did.
 * Verified, not assumed: the only write is {@code UpdateLastLoginUseCase.execute}, whose
 * {@code @Transactional(noRollbackFor = DataIntegrityViolationException.class)} is what makes
 * the {@code saveAndFlush} redelivery-race handling work — {@code save} therefore always
 * joins an ambient transaction and the flag was never load-bearing here.
 *
 * <p>The library's {@code deleteByProcessedAtBefore} retention query is not carried over:
 * account-service never called it.
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {

    boolean existsByEventId(String eventId);
}
