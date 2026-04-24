package com.example.order.infrastructure.event;

import com.example.messaging.outbox.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessedEventCleanupScheduler {

    private final ProcessedEventJpaRepository processedEventJpaRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int deleted = processedEventJpaRepository.deleteByProcessedAtBefore(cutoff);
        log.info("Cleaned up {} processed event records older than 30 days", deleted);
    }
}
