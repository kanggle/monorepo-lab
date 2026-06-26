package com.example.payment.adapter.out.event;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.OutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * payment-service outbox relay (TASK-BE-449, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5), replacing the v1
 * {@code PaymentEventOutboxRelay extends OutboxPollingScheduler}. Supplies the
 * payment-service specifics:
 * <ul>
 *   <li>the {@code payment_outbox} repository</li>
 *   <li>a {@link TopicResolver} mapping the four event types to their existing
 *       topics (ported verbatim from the v1 {@code PaymentEventOutboxRelay.resolveTopic},
 *       reusing {@link PaymentEventOutboxWriter}'s {@code EVENT_TYPE_*} constants,
 *       incl. reject-unmapped)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code payment} prefix, <b>wrapped</b>
 *       so a publish failure ALSO increments the pre-existing
 *       {@code event_publish_failure_total{service=payment-service}} counter via
 *       {@link PaymentMetricRecorder#incrementEventPublishFailure} — preserving the v1
 *       {@code onKafkaSendFailure} hook</li>
 *   <li>a preserved {@code payment.outbox.pending.count} gauge</li>
 *   <li>the {@code @Scheduled} trigger</li>
 * </ul>
 *
 * <p>{@code @Profile("!standalone")} + {@code @ConditionalOnProperty("outbox.polling.enabled")}
 * — the same gating the v1 relay used (so unit/slice tests and standalone disable the
 * background poller while the write path still persists rows). The gate property name
 * is intentionally kept as {@code outbox.polling.enabled} (the v2 timing knobs live
 * under {@code payment.outbox.*}).
 */
@Component
@Profile("!standalone")
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOutboxPublisher extends AbstractOutboxPublisher<PaymentOutboxEntity> {

    static final String TOPIC_PAYMENT_COMPLETED = "payment.payment.completed";
    static final String TOPIC_PAYMENT_REFUNDED = "payment.payment.refunded";
    static final String TOPIC_PAYMENT_REFUND_STRANDED = "payment.alert.refund.stranded";
    static final String TOPIC_PAYMENT_REFUND_UNRESOLVED = "payment.alert.refund.unresolved";

    public PaymentOutboxPublisher(PaymentOutboxRepository repository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  TransactionTemplate transactionTemplate,
                                  Clock clock,
                                  MeterRegistry meterRegistry,
                                  PaymentMetricRecorder paymentMetricRecorder,
                                  @Value("${payment.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                failurePreservingMetrics(meterRegistry, paymentMetricRecorder),
                clock,
                batchSize);

        Gauge.builder("payment.outbox.pending.count", repository,
                        PaymentOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished payment outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${payment.outbox.poll-ms:1000}",
            initialDelayString = "${payment.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return PaymentOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code PaymentEventOutboxRelay.resolveTopic}, incl. reject-unmapped.
     * Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown payment event type: null");
        }
        return switch (eventType) {
            case PaymentEventOutboxWriter.EVENT_TYPE_COMPLETED -> TOPIC_PAYMENT_COMPLETED;
            case PaymentEventOutboxWriter.EVENT_TYPE_REFUNDED -> TOPIC_PAYMENT_REFUNDED;
            case PaymentEventOutboxWriter.EVENT_TYPE_REFUND_STRANDED -> TOPIC_PAYMENT_REFUND_STRANDED;
            case PaymentEventOutboxWriter.EVENT_TYPE_REFUND_UNRESOLVED -> TOPIC_PAYMENT_REFUND_UNRESOLVED;
            default -> throw new IllegalArgumentException("Unknown payment event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also fires
     * the v1 {@link PaymentMetricRecorder#incrementEventPublishFailure} hook
     * ({@code event_publish_failure_total{service=payment-service}}). Guarded on
     * {@code eventType != null} so poll-level failures (null eventType) are not
     * double-counted — matching the v1 {@code onKafkaSendFailure} semantics.
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry,
                                                          PaymentMetricRecorder paymentMetricRecorder) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "payment");
        return new OutboxMetrics() {
            @Override
            public void recordPublishSuccess(String eventType, Duration lag) {
                base.recordPublishSuccess(eventType, lag);
            }

            @Override
            public void recordPublishFailure(String eventType, String reason) {
                base.recordPublishFailure(eventType, reason);
                if (eventType != null) {
                    paymentMetricRecorder.incrementEventPublishFailure(eventType);
                }
            }
        };
    }
}
