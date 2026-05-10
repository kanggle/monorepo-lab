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

    /**
     * Per-row publish outcome classification. Determines how
     * {@link #publishPendingEvents(EventSender)} treats the row and whether
     * batch drain continues.
     *
     * <ul>
     *   <li>{@link #SUCCESS} — broker ACK received. Row marked PUBLISHED, drain continues.</li>
     *   <li>{@link #FAILURE_TRANSIENT} — retryable failure (broker unreachable,
     *       timeout, etc.). Row remains PENDING, batch drain breaks to avoid
     *       a retry storm against a service-wide outage. Next poll retries the
     *       same row in arrival order.</li>
     *   <li>{@link #FAILURE_PERMANENT} — non-retryable failure (unknown event
     *       type, unserializable payload). Row marked FAILED and drain
     *       continues — a single poison-pill row no longer blocks the rest of
     *       the batch.</li>
     * </ul>
     */
    public enum SendOutcome {
        SUCCESS,
        FAILURE_TRANSIENT,
        FAILURE_PERMANENT
    }

    @FunctionalInterface
    public interface EventSender {
        SendOutcome send(String eventType, String aggregateId, String payload);
    }

    @Transactional
    public void publishPendingEvents(EventSender sender) {
        List<OutboxJpaEntity> pendingEntries = outboxJpaRepository
                .findPendingWithLock(PageRequest.of(0, batchSize));

        for (OutboxJpaEntity entry : pendingEntries) {
            SendOutcome outcome = sender.send(entry.getEventType(), entry.getAggregateId(), entry.getPayload());
            switch (outcome) {
                case SUCCESS -> {
                    entry.markPublished();
                    log.info("Outbox event published: id={}, eventType={}, aggregateId={}",
                            entry.getId(), entry.getEventType(), entry.getAggregateId());
                }
                case FAILURE_PERMANENT -> {
                    entry.markFailed();
                    log.error("Outbox event marked FAILED (permanent): id={}, eventType={}, aggregateId={}",
                            entry.getId(), entry.getEventType(), entry.getAggregateId());
                }
                case FAILURE_TRANSIENT -> {
                    log.warn("Outbox event publish failed, will retry: id={}, eventType={}",
                            entry.getId(), entry.getEventType());
                    return;
                }
            }
        }
    }
}
