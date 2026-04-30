package com.example.security.consumer.handler;

import com.example.messaging.outbox.ProcessedEventJpaRepository;
import com.example.security.infrastructure.redis.RedisEventDedupStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventDedupService {

    private final RedisEventDedupStore redisStore;
    private final ProcessedEventJpaRepository processedEventRepository;

    /**
     * Fast-path dedup check using Redis.
     * This is an optimization only; the authoritative check is the MySQL
     * UNIQUE constraint on processed_events, enforced inside the transaction
     * in RecordLoginHistoryUseCase.
     */
    public boolean isDuplicate(String eventId) {
        // Fast path: Redis
        if (redisStore.isDuplicate(eventId)) {
            log.debug("Dedup hit (Redis) for eventId={}", eventId);
            return true;
        }

        // Fallback: MySQL (outside transaction, advisory only)
        if (processedEventRepository.existsByEventId(eventId)) {
            log.debug("Dedup hit (MySQL) for eventId={}", eventId);
            // Restore Redis cache for future checks
            redisStore.markProcessed(eventId);
            return true;
        }

        return false;
    }

    /**
     * Mark event as processed in Redis only.
     * Called after the DB transaction commits successfully in RecordLoginHistoryUseCase.
     */
    public void markProcessedInRedis(String eventId) {
        redisStore.markProcessed(eventId);
        log.debug("Marked event in Redis: eventId={}", eventId);
    }
}
