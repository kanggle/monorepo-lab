package com.example.security.application;

import com.example.messaging.outbox.ProcessedEventJpaEntity;
import com.example.messaging.outbox.ProcessedEventJpaRepository;
import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records login history in a single transaction that also marks the event as processed.
 * The MySQL UNIQUE constraint on processed_events.event_id acts as the ultimate guard
 * against race conditions. Redis check in the consumer layer is a fast-path optimization only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordLoginHistoryUseCase {

    private final LoginHistoryRepository loginHistoryRepository;
    private final ProcessedEventJpaRepository processedEventRepository;

    /**
     * Atomically saves login history and marks the event as processed.
     *
     * @return true if the event was processed, false if it was a duplicate
     */
    @Transactional
    public boolean execute(LoginHistoryEntry entry, String eventType) {
        // Check MySQL as authoritative dedup within the transaction
        if (processedEventRepository.existsByEventId(entry.getEventId())) {
            log.info("Duplicate event detected in transaction: eventId={}", entry.getEventId());
            return false;
        }

        try {
            loginHistoryRepository.save(entry);
            processedEventRepository.save(ProcessedEventJpaEntity.create(entry.getEventId(), eventType));
            log.info("Recorded login history: eventId={}, accountId={}, outcome={}",
                    entry.getEventId(), entry.getAccountId(), entry.getOutcome());
            return true;
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted between our check and save.
            // This is expected behavior under concurrent duplicate delivery.
            log.info("Concurrent duplicate event handled via constraint: eventId={}", entry.getEventId());
            return false;
        }
    }
}
