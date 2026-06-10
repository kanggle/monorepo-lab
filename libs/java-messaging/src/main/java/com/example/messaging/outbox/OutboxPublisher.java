package com.example.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Isolation;
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

    /**
     * TASK-MONO-211 (ADR-MONO-004 § 4.7) — runs at {@code READ_COMMITTED}, NOT the
     * default {@code REPEATABLE_READ}. {@link OutboxJpaRepository#findPendingWithLock}
     * is a {@code SELECT … WHERE status='PENDING' … FOR UPDATE}; under REPEATABLE
     * READ that takes next-key/gap locks over the PENDING range, and because the
     * Kafka publish ({@code kafkaTemplate.send(...).get()}) happens inside this
     * transaction while the lock is held, a slow/warming broker made the poller
     * block concurrent business {@code INSERT}s into {@code outbox} for up to
     * {@code innodb_lock_wait_timeout} (50s → 1205 → PessimisticLockingFailureException
     * → the business mutation 500s). READ COMMITTED drops gap locking (only the
     * matched rows are locked, never the gaps), so business INSERTs proceed while
     * the poller publishes. Delivery semantics are unchanged: the {@code FOR UPDATE}
     * still row-locks the claimed PENDING rows (single-poller exclusivity, no
     * double-publish), the batch is read once then written (no re-read, so RC's
     * weaker repeatable-read is irrelevant), and at-least-once + FIFO
     * ({@code ORDER BY created_at}) are preserved. A slow Kafka now only degrades
     * poller throughput (its own batch waits) instead of failing business writes.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
