package com.wms.inventory.adapter.out.messaging;

import com.wms.inventory.adapter.out.persistence.outbox.InventoryOutboxJpaEntity;
import com.wms.inventory.adapter.out.persistence.outbox.InventoryOutboxJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls {@code inventory_outbox} for unpublished rows and forwards them to
 * Kafka. Each row is forwarded once per scheduler tick; on send failure, the
 * row stays {@code published_at IS NULL} so the next tick retries.
 *
 * <p>Backoff: applied on consecutive failure ticks via
 * {@link #nextDelayMillis()} — {@code 1s → 2s → 4s → 8s → 30s cap}. The
 * {@code @Scheduled} fixedDelay still fires every 500ms, but the publisher
 * skips real work while the in-memory backoff timer is active.
 *
 * <p>Topic resolution: each row's {@code event_type} is suffixed with
 * {@code .v1} and prefixed with {@code wms.}, mapping to the topics declared
 * in {@code inventory-events.md} § Topic Layout. Examples:
 * {@code inventory.received → wms.inventory.received.v1};
 * {@code inventory.low-stock-detected → wms.inventory.alert.v1}.
 *
 * <p>Disabled under {@code standalone} (no Kafka).
 */
@Component
@Profile("!standalone")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final InventoryOutboxJpaRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong nextAttemptEpochMs = new AtomicLong();

    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Timer publishLatencyTimer;

    public OutboxPublisher(InventoryOutboxJpaRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           TransactionTemplate transactionTemplate,
                           Clock clock,
                           MeterRegistry meterRegistry,
                           @Value("${inventory.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.publishSuccessCounter = Counter.builder("inventory.outbox.publish.success.total")
                .description("Outbox rows successfully forwarded to Kafka")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("inventory.outbox.publish.failure.total")
                .description("Outbox rows whose Kafka publish call failed")
                .register(meterRegistry);
        this.publishLatencyTimer = Timer.builder("inventory.outbox.lag.seconds")
                .description("Wall time between outbox row created_at and Kafka ACK")
                .register(meterRegistry);
        Gauge.builder("inventory.outbox.pending.count", repository,
                        InventoryOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished outbox rows")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.polling-interval-ms:500}",
            initialDelayString = "${inventory.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        if (clock.millis() < nextAttemptEpochMs.get()) {
            return; // Backoff timer is still active.
        }
        List<InventoryOutboxJpaEntity> pending;
        try {
            pending = transactionTemplate.execute(status ->
                    repository.findPending(PageRequest.of(0, batchSize)));
        } catch (RuntimeException e) {
            log.warn("Outbox poll failed: {}", e.getMessage());
            registerFailure();
            return;
        }
        if (pending == null || pending.isEmpty()) {
            consecutiveFailures.set(0);
            return;
        }

        for (InventoryOutboxJpaEntity row : pending) {
            try {
                publishOne(row);
                consecutiveFailures.set(0);
            } catch (RuntimeException e) {
                log.warn("Failed to publish outbox row {} ({}): {}",
                        row.getId(), row.getEventType(), e.getMessage());
                registerFailure();
                return; // Stop the batch — wait for backoff before retrying.
            }
        }
    }

    private void publishOne(InventoryOutboxJpaEntity row) {
        String topic = topicFor(row.getEventType());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, row.getPartitionKey(), row.getPayload());
        record.headers().add("eventId", row.getId().toString().getBytes());
        record.headers().add("eventType", row.getEventType().getBytes());
        try {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Kafka ACK", ie);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Kafka send failed for outbox row " + row.getId(), e);
        }

        transactionTemplate.executeWithoutResult(status -> {
            InventoryOutboxJpaEntity managed = repository.findById(row.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Outbox row vanished mid-publish: " + row.getId()));
            managed.markPublished(clock.instant());
            repository.save(managed);
        });
        publishSuccessCounter.increment();
        Duration lag = Duration.between(row.getCreatedAt(), clock.instant());
        publishLatencyTimer.record(lag);
    }

    private void registerFailure() {
        publishFailureCounter.increment();
        int failures = consecutiveFailures.incrementAndGet();
        long delay = nextDelayMillis(failures);
        nextAttemptEpochMs.set(clock.millis() + delay);
        log.info("Outbox publisher backing off {}ms after {} consecutive failures", delay, failures);
    }

    private static long nextDelayMillis(int failures) {
        long delay = (long) (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, failures - 1));
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    private static String topicFor(String eventType) {
        // inventory.low-stock-detected → wms.inventory.alert.v1 (per topic table)
        if ("inventory.low-stock-detected".equals(eventType)) {
            return "wms.inventory.alert.v1";
        }
        // inventory.X → wms.inventory.X.v1
        return "wms." + eventType + ".v1";
    }
}
