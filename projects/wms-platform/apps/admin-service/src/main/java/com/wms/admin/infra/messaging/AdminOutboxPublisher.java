package com.wms.admin.infra.messaging;

import com.wms.admin.infra.persistence.AdminOutboxJpaEntity;
import com.wms.admin.infra.persistence.AdminOutboxJpaRepository;
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
 * Polls {@code admin_outbox} for unpublished rows and forwards them to the
 * matching {@code wms.admin.<aggregate>.v1} topic. Mirrors the
 * notification-service pattern (TASK-BE-043).
 *
 * <p>Topic resolution: the {@code aggregate_type} column maps 1:1 to the
 * topic suffix — {@code user / role / assignment / setting} →
 * {@code wms.admin.user.v1 / .role.v1 / .assignment.v1 / .settings.v1}.
 *
 * <p>Disabled under {@code standalone} (no Kafka).
 */
@Component
@Profile("!standalone")
public class AdminOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(AdminOutboxPublisher.class);
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final AdminOutboxJpaRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;
    private final String userTopic;
    private final String roleTopic;
    private final String assignmentTopic;
    private final String settingsTopic;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong nextAttemptEpochMs = new AtomicLong();

    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    public AdminOutboxPublisher(AdminOutboxJpaRepository repository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                TransactionTemplate transactionTemplate,
                                Clock clock,
                                MeterRegistry meterRegistry,
                                @Value("${admin.outbox.batch-size:100}") int batchSize,
                                @Value("${admin.kafka.topics.user:wms.admin.user.v1}") String userTopic,
                                @Value("${admin.kafka.topics.role:wms.admin.role.v1}") String roleTopic,
                                @Value("${admin.kafka.topics.assignment:wms.admin.assignment.v1}") String assignmentTopic,
                                @Value("${admin.kafka.topics.settings:wms.admin.settings.v1}") String settingsTopic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.userTopic = userTopic;
        this.roleTopic = roleTopic;
        this.assignmentTopic = assignmentTopic;
        this.settingsTopic = settingsTopic;
        this.publishSuccessCounter = Counter.builder("admin.outbox.publish.success.total")
                .description("Outbox rows successfully forwarded to Kafka")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("admin.outbox.publish.failure.total")
                .description("Outbox rows whose Kafka publish call failed")
                .register(meterRegistry);
        Gauge.builder("admin.outbox.pending.count", repository,
                        AdminOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished outbox rows")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${admin.outbox.polling-interval-ms:500}",
            initialDelayString = "${admin.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        if (clock.millis() < nextAttemptEpochMs.get()) {
            return;
        }
        List<AdminOutboxJpaEntity> pending;
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
        for (AdminOutboxJpaEntity row : pending) {
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

    private void publishOne(AdminOutboxJpaEntity row) {
        String topic = resolveTopic(row.getAggregateType(), row.getEventType());
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
            AdminOutboxJpaEntity managed = repository.findById(row.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Outbox row vanished mid-publish: " + row.getId()));
            managed.markPublished(clock.instant());
            repository.save(managed);
        });
        publishSuccessCounter.increment();
    }

    /**
     * Maps {@code aggregate_type} to the topic — falls back to event-type
     * parsing for cascade events (assignment.revoked emitted under user /
     * role aggregateType keep aggregate_type=assignment via the service).
     */
    String resolveTopic(String aggregateType, String eventType) {
        return switch (aggregateType) {
            case "user" -> userTopic;
            case "role" -> roleTopic;
            case "assignment" -> assignmentTopic;
            case "setting" -> settingsTopic;
            default -> throw new IllegalArgumentException(
                    "Unsupported admin aggregate type: " + aggregateType + " (event=" + eventType + ")");
        };
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
