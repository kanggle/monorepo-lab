package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.repository.ProcessedEventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedupe over the local {@code processed_event} table. Runs inside the consumer's
 * transaction ({@code MANDATORY}) so the dedupe record and the ledger write commit
 * atomically (AC-6). A concurrent insert race resolves to "duplicate".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessedEventStoreImpl implements ProcessedEventStore {

    private final ProcessedEventJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isDuplicate(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("event_id null/blank — skipping dedupe (falling back to business key). eventType={}",
                    eventType);
            return false;
        }
        if (repository.existsByEventId(eventId)) {
            log.warn("Duplicate event, skipping. eventId={}, eventType={}", eventId, eventType);
            return true;
        }
        try {
            repository.save(ProcessedEventJpaEntity.create(eventId, eventType));
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event (concurrent insert), skipping. eventId={}, eventType={}",
                    eventId, eventType);
            return true;
        }
        return false;
    }
}
