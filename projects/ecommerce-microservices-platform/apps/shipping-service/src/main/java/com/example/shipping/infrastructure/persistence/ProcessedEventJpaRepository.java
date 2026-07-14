package com.example.shipping.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Owned by shipping-service (TASK-MONO-406 — moved out of {@code libs/java-messaging};
 * see {@link ProcessedEventJpaEntity}).
 *
 * <p>Scanned by {@code ShippingServiceApplication}'s
 * {@code @EnableJpaRepositories(basePackages = "com.example.shipping.infrastructure")}.
 * That config does not set {@code enableDefaultTransactions = false} as the library's
 * did, which is safe here and was verified rather than assumed: every write goes
 * through {@code EventDeduplicationChecker.isDuplicate}, annotated
 * {@code @Transactional(propagation = MANDATORY)}, so {@code save} can never run
 * outside the business transaction — a strictly stronger guarantee than the flag.
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {

    boolean existsByEventId(String eventId);

    @Modifying
    @Query("DELETE FROM ProcessedEventJpaEntity e WHERE e.processedAt < :cutoff")
    int deleteByProcessedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
