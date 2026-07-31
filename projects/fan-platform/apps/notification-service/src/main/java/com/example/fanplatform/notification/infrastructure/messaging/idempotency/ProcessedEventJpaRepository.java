package com.example.fanplatform.notification.infrastructure.messaging.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Spring Data repository for the consumer dedupe table ({@code processed_events}).
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {

    /**
     * Insert a dedupe row iff {@code eventId} is not already present, in a single
     * atomic native statement — see {@link EventDedupePortJpaAdapter} javadoc for
     * why this replaces the old {@code existsByEventId} + {@code save(...)} pair.
     *
     * @return 1 when the row was inserted (first sighting), 0 when it already existed (duplicate)
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, event_type, processed_at)
            VALUES (:eventId, :eventType, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("processedAt") Instant processedAt);
}
