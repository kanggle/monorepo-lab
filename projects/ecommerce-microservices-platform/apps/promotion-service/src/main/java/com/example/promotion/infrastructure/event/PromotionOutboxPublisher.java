package com.example.promotion.infrastructure.event;

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
 * promotion-service outbox publisher (TASK-BE-444, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5) — the polling loop, Kafka send,
 * mark-as-published, exponential backoff (1s → 2s → … → 30s cap) and the
 * {@code eventId}/{@code eventType} record headers all live in
 * {@code libs/java-messaging}. This class supplies the promotion-service
 * specifics:
 * <ul>
 *   <li>the {@code promotion_outbox} repository via
 *       {@link SpringDataOutboxRowRepository#wrap}</li>
 *   <li>a {@link TopicResolver} mapping {@code CouponUsed → promotion.coupon.used}
 *       and {@code CouponExpired → promotion.coupon.expired} (ported verbatim
 *       from the v1 {@code OutboxPollingScheduler.resolveTopic}, including the
 *       reject-unmapped validation — the existing topics carry NO {@code .v1}
 *       suffix, so the mapping is preserved exactly, not the
 *       {@code <eventType>.v1} convention)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code promotion} prefix plus
 *       a preserved {@code promotion.outbox.pending.count} {@link Gauge}</li>
 *   <li>the {@code @Scheduled} trigger driving {@link #publishPending()}</li>
 * </ul>
 *
 * <p>No {@code @Profile} gate — the v1 {@code OutboxPollingScheduler} ran
 * unconditionally; this preserves that.
 */
@Component
public class PromotionOutboxPublisher extends AbstractOutboxPublisher<PromotionOutboxEntity> {

    static final String TOPIC_COUPON_USED = "promotion.coupon.used";
    static final String TOPIC_COUPON_EXPIRED = "promotion.coupon.expired";

    public PromotionOutboxPublisher(PromotionOutboxRepository repository,
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    TransactionTemplate transactionTemplate,
                                    Clock clock,
                                    MeterRegistry meterRegistry,
                                    @Value("${promotion.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "promotion"),
                clock,
                batchSize);

        // Pending-count gauge stays per-service so the promotion.outbox.pending.count
        // metric name is available for dashboards/alerts (cf. master-service
        // preserving master.outbox.pending.count).
        Gauge.builder("promotion.outbox.pending.count", repository,
                        PromotionOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished promotion outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${promotion.outbox.poll-ms:1000}",
            initialDelayString = "${promotion.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return PromotionOutboxPublisher::topicFor;
    }

    /**
     * Resolves the domain event type to its Kafka topic. Ported verbatim from
     * the v1 {@code OutboxPollingScheduler.resolveTopic} — unknown event types
     * are rejected with {@link IllegalArgumentException} (treated as a
     * non-retryable poison-pill row by the publisher loop).
     *
     * <p>Exposed package-private + static so the unit test can exercise the
     * mapping + reject-unmapped behaviour directly.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown event type: null");
        }
        return switch (eventType) {
            case "CouponUsed" -> TOPIC_COUPON_USED;
            case "CouponExpired" -> TOPIC_COUPON_EXPIRED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
