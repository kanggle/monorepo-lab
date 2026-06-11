package com.example.fanplatform.notification.infrastructure.messaging.idempotency;

import com.example.fanplatform.notification.application.port.ProcessedEventStore;
import com.example.fanplatform.notification.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA-backed {@link ProcessedEventStore}. {@code markProcessed} runs inside the
 * caller's transaction so the dedupe row commits atomically with the
 * {@code Notification} row.
 */
@Component
@RequiredArgsConstructor
public class JpaProcessedEventStore implements ProcessedEventStore {

    private final ProcessedEventJpaRepository repository;
    private final ClockPort clock;

    @Override
    public boolean alreadyProcessed(String eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    public void markProcessed(String eventId, String eventType) {
        repository.save(ProcessedEventJpaEntity.create(eventId, eventType, clock.now()));
    }
}
