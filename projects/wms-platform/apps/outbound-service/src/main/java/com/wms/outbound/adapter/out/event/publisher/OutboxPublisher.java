package com.wms.outbound.adapter.out.event.publisher;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.OutboxRowRepository;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.wms.outbound.adapter.out.persistence.entity.OutboundOutboxEntity;
import com.wms.outbound.adapter.out.persistence.repository.OutboundOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbound-service outbox publisher.
 *
 * <p>Now a thin wrapper around the shared
 * {@link AbstractOutboxPublisher} (TASK-MONO-049 / ADR-MONO-004) — the polling
 * loop, Kafka send, mark-as-published and exponential backoff are all in
 * {@code libs/java-messaging}. This class supplies the outbound-service
 * specifics:
 * <ul>
 *   <li>{@code outbound_outbox} repository through {@link SpringDataOutboxRowRepository#wrap}</li>
 *   <li>{@link TopicResolver} mapping {@code outbound.X} → {@code wms.outbound.X.v1}</li>
 *   <li>{@link MicrometerOutboxMetrics} with the {@code outbound} prefix and a
 *       {@link Gauge} for {@code outbound.outbox.pending.count}</li>
 *   <li>The {@code @Scheduled} annotation that drives {@link #publishPending()}</li>
 * </ul>
 *
 * <p>Disabled under the {@code standalone} profile (no Kafka).
 */
@Component
@Profile("!standalone")
public class OutboxPublisher extends AbstractOutboxPublisher<OutboundOutboxEntity> {

    public OutboxPublisher(OutboundOutboxRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           TransactionTemplate transactionTemplate,
                           Clock clock,
                           MeterRegistry meterRegistry,
                           @Value("${outbound.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "outbound"),
                clock,
                batchSize);

        // Pending-count gauge stays per-service so the existing
        // outbound.outbox.pending.count metric name is preserved.
        Gauge.builder("outbound.outbox.pending.count", repository,
                        OutboundOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished outbound outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${outbound.outbox.poll-ms:1000}",
            initialDelayString = "${outbound.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return eventType -> "wms." + eventType + ".v1";
    }

    /**
     * Static topic-resolution helper retained for backward compatibility with
     * existing tests that exercise the topic naming convention directly.
     */
    static String topicFor(String eventType) {
        return topicResolver().resolveTopic(eventType);
    }
}
