package com.wms.inventory.adapter.out.messaging;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.wms.inventory.adapter.out.persistence.outbox.InventoryOutboxJpaEntity;
import com.wms.inventory.adapter.out.persistence.outbox.InventoryOutboxJpaRepository;
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
 * Inventory-service outbox publisher.
 *
 * <p>Thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004). The publish loop, exponential backoff,
 * Kafka send, and mark-as-published live in {@code libs/java-messaging};
 * this class supplies the inventory-service specifics (topic mapping,
 * metric prefix, schedule cadence).
 *
 * <p>Topic resolution: {@code inventory.X → wms.inventory.X.v1} per
 * {@code inventory-events.md} § Topic Layout. Special case:
 * {@code inventory.low-stock-detected → wms.inventory.alert.v1}.
 *
 * <p>Disabled under the {@code standalone} profile (no Kafka).
 */
@Component
@Profile("!standalone")
public class OutboxPublisher extends AbstractOutboxPublisher<InventoryOutboxJpaEntity> {

    public OutboxPublisher(InventoryOutboxJpaRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           TransactionTemplate transactionTemplate,
                           Clock clock,
                           MeterRegistry meterRegistry,
                           @Value("${inventory.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "inventory"),
                clock,
                batchSize);

        Gauge.builder("inventory.outbox.pending.count", repository,
                        InventoryOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${inventory.outbox.polling-interval-ms:500}",
            initialDelayString = "${inventory.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return eventType -> {
            // inventory.low-stock-detected → wms.inventory.alert.v1 (per topic table)
            if ("inventory.low-stock-detected".equals(eventType)) {
                return "wms.inventory.alert.v1";
            }
            // inventory.X → wms.inventory.X.v1
            return "wms." + eventType + ".v1";
        };
    }

    /** Backward-compatibility helper for existing tests. */
    static String topicFor(String eventType) {
        return topicResolver().resolveTopic(eventType);
    }
}
