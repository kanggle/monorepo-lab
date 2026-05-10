package com.wms.inbound.adapter.out.messaging;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.wms.inbound.adapter.out.persistence.outbox.InboundOutboxJpaEntity;
import com.wms.inbound.adapter.out.persistence.outbox.InboundOutboxJpaRepository;
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
 * Inbound-service outbox publisher.
 *
 * <p>Thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004). Disabled under {@code standalone} (no Kafka).
 */
@Component
@Profile("!standalone")
public class OutboxPublisher extends AbstractOutboxPublisher<InboundOutboxJpaEntity> {

    public OutboxPublisher(InboundOutboxJpaRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           TransactionTemplate transactionTemplate,
                           Clock clock,
                           MeterRegistry meterRegistry,
                           @Value("${inbound.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "inbound"),
                clock,
                batchSize);

        Gauge.builder("inbound.outbox.pending.count", repository,
                        InboundOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished inbound outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${inbound.outbox.poll-ms:1000}",
            initialDelayString = "${inbound.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return eventType -> "wms." + eventType + ".v1";
    }

    /** Backward-compatibility helper for existing tests. */
    static String topicFor(String eventType) {
        return topicResolver().resolveTopic(eventType);
    }
}
