package com.example.fanplatform.notification.infrastructure.messaging.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the consumer dedupe table.
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {

    boolean existsByEventId(String eventId);
}
