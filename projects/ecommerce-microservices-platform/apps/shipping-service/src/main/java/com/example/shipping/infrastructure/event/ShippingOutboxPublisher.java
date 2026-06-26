package com.example.shipping.infrastructure.event;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * shipping-service outbox publisher (TASK-BE-446, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5). Supplies the shipping-service specifics:
 * the {@code shipping_outbox} repository, a {@link TopicResolver} mapping the
 * three event types to their existing topics (ported verbatim from the v1
 * {@code OutboxPollingScheduler.resolveTopic} — note the <b>mixed</b> topic
 * conventions: {@code ShippingStatusChanged} has no {@code .v1} suffix while the
 * two wms-bound fulfillment legs do; preserved exactly), a
 * {@link MicrometerOutboxMetrics} with the {@code shipping} prefix + a preserved
 * {@code shipping.outbox.pending.count} gauge, and the {@code @Scheduled} trigger.
 *
 * <p>No {@code @Profile} gate — the v1 {@code OutboxPollingScheduler} ran
 * unconditionally; this preserves that.
 */
@Component
public class ShippingOutboxPublisher extends AbstractOutboxPublisher<ShippingOutboxEntity> {

    static final String TOPIC_SHIPPING_STATUS_CHANGED = "shipping.shipping.status-changed";
    static final String TOPIC_FULFILLMENT_REQUESTED = "ecommerce.fulfillment.requested.v1";
    static final String TOPIC_MANUAL_SHIP_CONFIRM_REQUESTED = "ecommerce.shipping.manual-confirm-requested.v1";

    public ShippingOutboxPublisher(ShippingOutboxRepository repository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   TransactionTemplate transactionTemplate,
                                   Clock clock,
                                   MeterRegistry meterRegistry,
                                   @Value("${shipping.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "shipping"),
                clock,
                batchSize);

        Gauge.builder("shipping.outbox.pending.count", repository,
                        ShippingOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished shipping outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${shipping.outbox.poll-ms:1000}",
            initialDelayString = "${shipping.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return ShippingOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event-type routing key to its Kafka topic. Ported
     * verbatim from the v1 {@code OutboxPollingScheduler.resolveTopic}, including
     * the mixed {@code .v1}/no-suffix conventions and reject-unmapped. Exposed
     * package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown event type: null");
        }
        return switch (eventType) {
            case "ShippingStatusChanged" -> TOPIC_SHIPPING_STATUS_CHANGED;
            case "FulfillmentRequested" -> TOPIC_FULFILLMENT_REQUESTED;
            case "ManualShipConfirmRequested" -> TOPIC_MANUAL_SHIP_CONFIRM_REQUESTED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
