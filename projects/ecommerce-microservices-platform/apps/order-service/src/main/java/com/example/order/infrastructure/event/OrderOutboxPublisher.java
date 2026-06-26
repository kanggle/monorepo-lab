package com.example.order.infrastructure.event;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.OutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.infrastructure.persistence.OrderOutboxEntity;
import com.example.order.infrastructure.persistence.OrderOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * order-service outbox publisher (TASK-BE-448, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5). Supplies the order-service specifics:
 * <ul>
 *   <li>the {@code order_outbox} repository</li>
 *   <li>a {@link TopicResolver} mapping the four event types to their existing
 *       topics (ported verbatim from the v1 {@code OutboxPollingScheduler.resolveTopic},
 *       incl. reject-unmapped)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code order} prefix, <b>wrapped</b>
 *       so a publish failure ALSO increments the pre-existing
 *       {@code event_publish_failure_total{service=order-service}} counter via
 *       {@link OrderMetricsPort#recordEventPublishFailure} — preserving the v1
 *       {@code onKafkaSendFailure} hook (dashboard/alert continuity)</li>
 *   <li>a preserved {@code order.outbox.pending.count} gauge</li>
 *   <li>the {@code @Scheduled} trigger</li>
 * </ul>
 *
 * <p>{@code @Profile("!standalone")} — the standalone (H2, no Kafka) profile has no
 * relay (matching the v1 scheduler); the write path is replaced there by
 * {@code StandaloneOrderEventPublisher}.
 */
@Component
@Profile("!standalone")
public class OrderOutboxPublisher extends AbstractOutboxPublisher<OrderOutboxEntity> {

    static final String TOPIC_ORDER_PLACED = "order.order.placed";
    static final String TOPIC_ORDER_CONFIRMED = "order.order.confirmed";
    static final String TOPIC_ORDER_CANCELLED = "order.order.cancelled";
    static final String TOPIC_ORDER_SAGA_RECOVERY_EXHAUSTED = "order.alert.saga.recovery.exhausted";

    public OrderOutboxPublisher(OrderOutboxRepository repository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                TransactionTemplate transactionTemplate,
                                Clock clock,
                                MeterRegistry meterRegistry,
                                OrderMetricsPort orderMetrics,
                                @Value("${order.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                failurePreservingMetrics(meterRegistry, orderMetrics),
                clock,
                batchSize);

        Gauge.builder("order.outbox.pending.count", repository,
                        OrderOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished order outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${order.outbox.poll-ms:1000}",
            initialDelayString = "${order.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return OrderOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code OutboxPollingScheduler.resolveTopic}, incl. reject-unmapped.
     * Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown event type: null");
        }
        return switch (eventType) {
            case "OrderPlaced" -> TOPIC_ORDER_PLACED;
            case "OrderConfirmed" -> TOPIC_ORDER_CONFIRMED;
            case "OrderCancelled" -> TOPIC_ORDER_CANCELLED;
            case "OrderSagaRecoveryExhausted" -> TOPIC_ORDER_SAGA_RECOVERY_EXHAUSTED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also
     * increments the v1 {@code event_publish_failure_total} counter (via
     * {@link OrderMetricsPort#recordEventPublishFailure}). The v1 scheduler fired
     * that hook only on a real send failure (a row with a known eventType), so the
     * wrapper guards on {@code eventType != null} (poll-level failures, which the
     * lib reports with a null eventType, are not double-counted into the order
     * metric — matching v1).
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry,
                                                          OrderMetricsPort orderMetrics) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "order");
        return new OutboxMetrics() {
            @Override
            public void recordPublishSuccess(String eventType, Duration lag) {
                base.recordPublishSuccess(eventType, lag);
            }

            @Override
            public void recordPublishFailure(String eventType, String reason) {
                base.recordPublishFailure(eventType, reason);
                if (eventType != null) {
                    orderMetrics.recordEventPublishFailure(eventType);
                }
            }
        };
    }
}
