package com.example.shipping.infrastructure.event;

import com.example.shipping.infrastructure.persistence.ProcessedEventJpaEntity;
import com.example.shipping.infrastructure.persistence.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        // No try/catch here, deliberately (TASK-BE-541). ProcessedEventJpaEntity uses an
        // assigned @Id, so Hibernate queues this INSERT until the commit-time flush — which
        // runs after this method returns. A catch around save() could never fire; the one
        // that used to sit here was dead code.
        //
        // The concurrent-duplicate case is still handled correctly, by retry rather than by
        // catch: the loser's flush fails, the consumer transaction rolls back, and on
        // redelivery the existsByEventId check above sees the winner's committed row and
        // returns true. Catching here could not improve on that — MANDATORY propagation
        // means we are inside the consumer's transaction, and a flushed constraint violation
        // marks it rollback-only, so "catch and carry on" is not available at this layer.
        processedEventJpaRepository.save(ProcessedEventJpaEntity.create(eventId, eventType));
        return false;
    }
}
