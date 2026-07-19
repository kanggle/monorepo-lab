package com.wms.notification.adapter.outbound.messaging;

import com.wms.notification.adapter.outbound.persistence.jpa.outbox.NotificationOutboxJpaEntity;
import com.wms.notification.adapter.outbound.persistence.jpa.outbox.NotificationOutboxJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
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
 * Polls {@code notification_outbox} for unpublished rows and forwards them
 * to Kafka. Mirrors the {@code inventory-service} pattern (TASK-BE-022).
 *
 * <p>Disabled under {@code standalone} (no Kafka).
 *
 * <h2>Why not extend libs/java-messaging OutboxPollingScheduler?</h2>
 *
 * <p>The libs base reads from a TEXT-payload {@code outbox} table; this
 * service mandates JSONB payload + partition_key in {@code notification_outbox}
 * (architecture.md § Persistence). A service-local poller keeps the schema
 * intact without weighing the libs base toward JSONB compatibility.
 */
@Component
@Profile("!standalone")
public class OutboxPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingScheduler.class);
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    /**
     * The only event type forwarded to Kafka in v1. The outbox also stores
     * {@code notification.delivery.scheduled} rows, but the contract
     * ({@code notification-events.md} § Out of Scope) says those are NOT
     * published in v1 — the publisher's topic resolver forwards only
     * {@code notification.delivered}. (May be relaxed in v2.)
     */
    private static final String EVENT_DELIVERED = "notification.delivered";

    private final NotificationOutboxJpaRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;
    private final String publishedTopic;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong nextAttemptEpochMs = new AtomicLong();

    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter outboxSkippedCounter;

    public OutboxPollingScheduler(NotificationOutboxJpaRepository repository,
                                       KafkaTemplate<String, String> kafkaTemplate,
                                       TransactionTemplate transactionTemplate,
                                       Clock clock,
                                       MeterRegistry meterRegistry,
                                       @Value("${wms.notification.outbox.batch-size:100}") int batchSize,
                                       @Value("${wms.kafka.topics.notification-delivered:wms.notification.delivered.v1}")
                                               String publishedTopic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.publishedTopic = publishedTopic;
        this.publishSuccessCounter = Counter.builder("notification.outbox.publish.success.total")
                .description("Outbox rows successfully forwarded to Kafka")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("notification.outbox.publish.failure.total")
                .description("Outbox rows whose Kafka publish call failed")
                .register(meterRegistry);
        this.outboxSkippedCounter = Counter.builder("notification.outbox.skipped.total")
                .description("Outbox rows drained without Kafka forwarding (out-of-scope event types in v1)")
                .register(meterRegistry);
        Gauge.builder("notification.outbox.pending.count", repository,
                        NotificationOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished outbox rows")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${wms.notification.outbox.polling-interval-ms:500}",
            initialDelayString = "${wms.notification.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        if (clock.millis() < nextAttemptEpochMs.get()) {
            return;
        }
        List<NotificationOutboxJpaEntity> pending;
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
        for (NotificationOutboxJpaEntity row : pending) {
            try {
                publishOne(row);
                consecutiveFailures.set(0);
            } catch (RuntimeException e) {
                log.warn("Failed to publish outbox row {} ({}): {}",
                        row.getId(), row.getEventType(), e.getMessage());
                registerFailure();
                return;
            }
        }
    }

    private void publishOne(NotificationOutboxJpaEntity row) {
        // Topic resolver (notification-events.md § Out of Scope): v1 forwards ONLY
        // `notification.delivered`. `notification.delivery.scheduled` rows are written
        // to the outbox but must NOT reach Kafka in v1 — drain them (mark published so
        // they are not re-polled forever) without sending. Relaxed in v2 by adding the
        // type here.
        boolean forwardToKafka = EVENT_DELIVERED.equals(row.getEventType());

        if (forwardToKafka) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(publishedTopic, row.getPartitionKey(), row.getPayload());
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
        } else {
            log.debug("Outbox row {} ({}) drained without Kafka forwarding — not published in v1",
                    row.getId(), row.getEventType());
        }

        transactionTemplate.executeWithoutResult(status -> {
            NotificationOutboxJpaEntity managed = repository.findById(row.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Outbox row vanished mid-publish: " + row.getId()));
            managed.markPublished(clock.instant());
            repository.save(managed);
        });

        if (forwardToKafka) {
            publishSuccessCounter.increment();
        } else {
            outboxSkippedCounter.increment();
        }
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
}
