package com.wms.inbound.adapter.out.messaging;

import com.wms.inbound.adapter.out.persistence.outbox.InboundOutboxJpaEntity;
import com.wms.inbound.adapter.out.persistence.outbox.InboundOutboxJpaRepository;
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

@Component
@Profile("!standalone")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final InboundOutboxJpaRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong nextAttemptEpochMs = new AtomicLong();

    private final Counter publishFailureCounter;
    private final Timer publishLatencyTimer;

    public OutboxPublisher(InboundOutboxJpaRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           TransactionTemplate transactionTemplate,
                           Clock clock,
                           MeterRegistry meterRegistry,
                           @Value("${inbound.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.publishFailureCounter = Counter.builder("inbound.outbox.publish.failure.total")
                .description("Outbox rows whose Kafka publish call failed")
                .register(meterRegistry);
        this.publishLatencyTimer = Timer.builder("inbound.outbox.lag.seconds")
                .description("Wall time between outbox row created_at and Kafka ACK")
                .register(meterRegistry);
        Gauge.builder("inbound.outbox.pending.count", repository,
                        InboundOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished inbound outbox rows")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${inbound.outbox.poll-ms:1000}",
            initialDelayString = "${inbound.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        if (clock.millis() < nextAttemptEpochMs.get()) {
            return;
        }
        List<InboundOutboxJpaEntity> pending;
        try {
            pending = transactionTemplate.execute(status ->
                    repository.findPending(PageRequest.of(0, batchSize)));
        } catch (RuntimeException e) {
            log.warn("Inbound outbox poll failed: {}", e.getMessage());
            registerFailure();
            return;
        }
        if (pending == null || pending.isEmpty()) {
            consecutiveFailures.set(0);
            return;
        }
        for (InboundOutboxJpaEntity row : pending) {
            try {
                publishOne(row);
                consecutiveFailures.set(0);
            } catch (RuntimeException e) {
                log.warn("Failed to publish inbound outbox row {} ({}): {}",
                        row.getId(), row.getEventType(), e.getMessage());
                registerFailure();
                return;
            }
        }
    }

    private void publishOne(InboundOutboxJpaEntity row) {
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
            throw new RuntimeException("Kafka send failed for inbound outbox row " + row.getId(), e);
        }
        transactionTemplate.executeWithoutResult(status -> {
            InboundOutboxJpaEntity managed = repository.findById(row.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inbound outbox row vanished mid-publish: " + row.getId()));
            managed.markPublished(clock.instant());
            repository.save(managed);
        });
        Duration lag = Duration.between(row.getCreatedAt(), clock.instant());
        publishLatencyTimer.record(lag);
    }

    private void registerFailure() {
        publishFailureCounter.increment();
        int failures = consecutiveFailures.incrementAndGet();
        long delay = nextDelayMillis(failures);
        nextAttemptEpochMs.set(clock.millis() + delay);
        log.info("Inbound outbox publisher backing off {}ms after {} consecutive failures", delay, failures);
    }

    private static long nextDelayMillis(int failures) {
        long delay = (long) (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, failures - 1));
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    static String topicFor(String eventType) {
        return "wms." + eventType + ".v1";
    }
}
