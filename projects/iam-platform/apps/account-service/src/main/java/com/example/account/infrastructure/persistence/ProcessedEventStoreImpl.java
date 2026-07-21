package com.example.account.infrastructure.persistence;

import com.example.account.domain.repository.ProcessedEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed {@link ProcessedEventStore}. Keeps the {@code processed_events} entity and
 * repository behind the port so the consumer application path does not import them directly.
 */
@Repository
@RequiredArgsConstructor
public class ProcessedEventStoreImpl implements ProcessedEventStore {

    private final ProcessedEventJpaRepository processedEventRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }

    @Override
    public void markProcessed(String eventId, String eventType) {
        // saveAndFlush forces the INSERT immediately so a concurrent redelivery's
        // unique-constraint violation is raised synchronously (as
        // DataIntegrityViolationException) here rather than at deferred commit-time flush.
        processedEventRepository.saveAndFlush(ProcessedEventJpaEntity.create(eventId, eventType));
    }
}
