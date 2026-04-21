package com.example.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxJpaRepository outboxJpaRepository;

    @Value("${outbox.polling.batch-size:50}")
    private int batchSize;

    @FunctionalInterface
    public interface EventSender {
        boolean send(String eventType, String aggregateId, String payload);
    }

    @Transactional
    public void publishPendingEvents(EventSender sender) {
        List<OutboxJpaEntity> pendingEntries = outboxJpaRepository
                .findPendingWithLock(PageRequest.of(0, batchSize));

        for (OutboxJpaEntity entry : pendingEntries) {
            boolean success = sender.send(entry.getEventType(), entry.getAggregateId(), entry.getPayload());
            if (success) {
                entry.markPublished();
                log.info("Outbox event published: id={}, eventType={}, aggregateId={}",
                        entry.getId(), entry.getEventType(), entry.getAggregateId());
            } else {
                log.warn("Outbox event publish failed, will retry: id={}, eventType={}",
                        entry.getId(), entry.getEventType());
                break;
            }
        }
    }
}
