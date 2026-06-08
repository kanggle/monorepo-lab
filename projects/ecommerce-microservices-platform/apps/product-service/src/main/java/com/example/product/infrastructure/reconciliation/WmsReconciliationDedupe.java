package com.example.product.infrastructure.reconciliation;

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
 * Idempotent-consumer dedupe (T8) for the wms reconciliation leg. Runs inside the
 * consumer's transaction ({@code MANDATORY}) so the dedupe row commits atomically with
 * the stock mutation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsReconciliationDedupe {

    private final WmsProcessedEventRepository processedEventRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isDuplicate(UUID eventId, String eventType) {
        if (eventId == null) {
            log.warn("wms reconciliation event has null eventId — skipping dedupe. eventType={}", eventType);
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Duplicate wms reconciliation event, skipping. eventId={}, eventType={}", eventId, eventType);
            return true;
        }
        try {
            processedEventRepository.save(
                    WmsProcessedEventEntity.of(eventId, eventType, Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate wms reconciliation event (concurrent), skipping. eventId={}", eventId);
            return true;
        }
        return false;
    }
}
