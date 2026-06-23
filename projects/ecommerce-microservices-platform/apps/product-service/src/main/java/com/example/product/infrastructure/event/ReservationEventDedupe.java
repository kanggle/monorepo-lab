package com.example.product.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent-consumer dedupe for the reservation saga (TASK-BE-428). Runs inside the consumer's
 * transaction ({@code MANDATORY}) so the dedupe row commits atomically with the reservation /
 * stock mutation. Mirrors {@code WmsReconciliationDedupe}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventDedupe {

    private final ReservationProcessedEventRepository processedEventRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isDuplicate(UUID eventId, String eventType) {
        if (eventId == null) {
            log.warn("reservation event has null eventId — skipping dedupe. eventType={}", eventType);
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Duplicate reservation event, skipping. eventId={}, eventType={}", eventId, eventType);
            return true;
        }
        try {
            processedEventRepository.save(
                    ReservationProcessedEventEntity.of(eventId, eventType, Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate reservation event (concurrent), skipping. eventId={}", eventId);
            return true;
        }
        return false;
    }
}
