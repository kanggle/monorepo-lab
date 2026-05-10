package com.example.messaging.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic outbox publisher that drives any service's outbox table to Kafka.
 *
 * <p>Each service extends this class with its concrete {@link OutboxRow}
 * implementation, supplies a {@link OutboxRowRepository}, a {@link TopicResolver},
 * and an {@link OutboxMetrics} adapter. The service then schedules its subclass
 * with {@code @Scheduled} (or any other trigger).
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@link #publishPending()} polls the repository under a transaction,
 *       publishes each row sequentially, and marks each as published in a fresh
 *       transaction after Kafka ACK.</li>
 *   <li>On a Kafka failure the row stays {@code published_at IS NULL} so the
 *       next tick retries.</li>
 *   <li>Exponential backoff (1s → 2s → 4s → 8s → 30s cap) is applied across
 *       failed ticks. The poll trigger keeps firing on its fixed delay; the
 *       publisher silently skips while the in-memory backoff timer is active.</li>
 *   <li>Each Kafka record carries {@code eventId} and {@code eventType} headers
 *       (UTF-8 bytes) so consumers can short-circuit before deserializing the
 *       payload.</li>
 * </ul>
 *
 * <p>This class is transport-only — it never references domain types. The row
 * interface keeps every domain detail inside the per-service module.
 */
public abstract class AbstractOutboxPublisher<R extends OutboxRow> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOutboxPublisher.class);

    private static final long DEFAULT_INITIAL_BACKOFF_MS = 1_000L;
    private static final long DEFAULT_MAX_BACKOFF_MS = 30_000L;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final long DEFAULT_KAFKA_SEND_TIMEOUT_SECONDS = 10L;

    private final OutboxRowRepository<R> repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TopicResolver topicResolver;
    private final OutboxMetrics metrics;
    private final Clock clock;
    private final int batchSize;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong nextAttemptEpochMs = new AtomicLong();

    protected AbstractOutboxPublisher(OutboxRowRepository<R> repository,
                                      KafkaTemplate<String, String> kafkaTemplate,
                                      TransactionTemplate transactionTemplate,
                                      TopicResolver topicResolver,
                                      OutboxMetrics metrics,
                                      Clock clock,
                                      int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.topicResolver = topicResolver;
        this.metrics = metrics;
        this.clock = clock;
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * Drive one polling cycle: load pending rows, publish them sequentially,
     * mark each published after Kafka ACK. Schedule this from the subclass with
     * an appropriate {@code @Scheduled(fixedDelayString = ...)} annotation.
     */
    public void publishPending() {
        if (clock.millis() < nextAttemptEpochMs.get()) {
            return; // Backoff window still active.
        }
        List<R> pending;
        try {
            pending = transactionTemplate.execute(status -> repository.findPending(batchSize));
        } catch (RuntimeException e) {
            log.warn("Outbox poll failed: {}", e.getMessage());
            registerFailure(null, e);
            return;
        }
        if (pending == null || pending.isEmpty()) {
            consecutiveFailures.set(0);
            return;
        }
        for (R row : pending) {
            try {
                publishOne(row);
                consecutiveFailures.set(0);
            } catch (RuntimeException e) {
                log.warn("Failed to publish outbox row {} ({}): {}",
                        row.getEventId(), row.getEventType(), e.getMessage());
                registerFailure(row.getEventType(), e);
                return; // Stop the batch; backoff will kick in.
            }
        }
    }

    private void publishOne(R row) {
        String topic = topicResolver.resolveTopic(row.getEventType());
        String key = row.getPartitionKey() != null ? row.getPartitionKey() : row.getAggregateId();
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, row.getPayload());
        record.headers().add("eventId", row.getEventId().toString().getBytes());
        record.headers().add("eventType", row.getEventType().getBytes());
        try {
            kafkaTemplate.send(record).get(DEFAULT_KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Kafka ACK", ie);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Kafka send failed for outbox row " + row.getEventId(), e);
        }

        transactionTemplate.executeWithoutResult(status -> {
            R managed = repository.findById(row.getEventId());
            if (managed == null) {
                log.warn("Outbox row {} vanished mid-publish", row.getEventId());
                return;
            }
            managed.markPublished(clock.instant());
            repository.save(managed);
        });

        Duration lag = Duration.between(row.getOccurredAt(), clock.instant());
        metrics.recordPublishSuccess(row.getEventType(), lag);
    }

    private void registerFailure(String eventType, Throwable cause) {
        String reason = cause == null ? "unknown" : cause.getClass().getSimpleName();
        metrics.recordPublishFailure(eventType, reason);
        int failures = consecutiveFailures.incrementAndGet();
        long delay = nextDelayMillis(failures);
        nextAttemptEpochMs.set(clock.millis() + delay);
        log.info("Outbox publisher backing off {}ms after {} consecutive failures", delay, failures);
    }

    /**
     * Compute the next backoff delay. Exposed protected for tests.
     */
    protected long nextDelayMillis(int failures) {
        long delay = (long) (DEFAULT_INITIAL_BACKOFF_MS
                * Math.pow(DEFAULT_BACKOFF_MULTIPLIER, Math.max(0, failures - 1)));
        return Math.min(delay, DEFAULT_MAX_BACKOFF_MS);
    }
}
