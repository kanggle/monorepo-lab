package com.example.shipping.infrastructure.event;

import com.example.messaging.outbox.ProcessedEventJpaEntity;
import com.example.messaging.outbox.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventDeduplicationChecker {

    private final ProcessedEventJpaRepository processedEventJpaRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isDuplicate(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("event_id is null or blank, skipping deduplication check. eventType={}", eventType);
            return false;
        }

        if (processedEventJpaRepository.existsByEventId(eventId)) {
            log.warn("Duplicate event detected, skipping. eventId={}, eventType={}", eventId, eventType);
            return true;
        }

        try {
            processedEventJpaRepository.save(ProcessedEventJpaEntity.create(eventId, eventType));
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event detected (concurrent insert), skipping. eventId={}, eventType={}", eventId, eventType);
            return true;
        }

        return false;
    }
}
