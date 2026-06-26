package com.wms.master.adapter.out.messaging;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.wms.master.adapter.out.persistence.outbox.MasterOutboxEntity;
import com.wms.master.adapter.out.persistence.outbox.MasterOutboxRepository;
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
 * Master-service outbox publisher (TASK-BE-438, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5) — the polling loop, Kafka send,
 * mark-as-published, exponential backoff (1s → 2s → … → 30s cap) and the
 * {@code eventId}/{@code eventType} record headers all live in
 * {@code libs/java-messaging}. This class supplies the master-service specifics:
 * <ul>
 *   <li>the {@code master_outbox} repository via {@link SpringDataOutboxRowRepository#wrap}</li>
 *   <li>a {@link TopicResolver} mapping {@code master.<aggregate>.<action>} →
 *       {@code wms.master.<aggregate>.v1} (ported verbatim from the v1
 *       {@code MasterOutboxPollingScheduler.resolveTopic}, including the
 *       reject-unmapped validation — see {@code master-events.md} § Topic Layout)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code master} prefix
 *       (emits {@code master.outbox.publish.success.total},
 *       {@code master.outbox.publish.failure.total},
 *       {@code master.outbox.lag.seconds}) plus a preserved
 *       {@code master.outbox.pending.count} {@link Gauge}</li>
 *   <li>the {@code @Scheduled} trigger driving {@link #publishPending()}</li>
 * </ul>
 *
 * <p>Disabled under the {@code standalone} profile (no Kafka). The write path
 * still persists {@code master_outbox} rows under standalone; they are simply
 * never drained.
 */
@Component
@Profile("!standalone")
public class MasterOutboxPublisher extends AbstractOutboxPublisher<MasterOutboxEntity> {

    private static final String TOPIC_PREFIX = "wms.master.";
    private static final String TOPIC_SUFFIX = ".v1";

    public MasterOutboxPublisher(MasterOutboxRepository repository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 TransactionTemplate transactionTemplate,
                                 Clock clock,
                                 MeterRegistry meterRegistry,
                                 @Value("${master.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "master"),
                clock,
                batchSize);

        // Pending-count gauge stays per-service so the existing
        // master.outbox.pending.count metric name is preserved (dashboard/alert
        // continuity — cf. outbound-service preserving outbound.outbox.pending.count).
        Gauge.builder("master.outbox.pending.count", repository,
                        MasterOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished master outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${master.outbox.poll-ms:1000}",
            initialDelayString = "${master.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    /**
     * Resolves {@code master.warehouse.created} → {@code wms.master.warehouse.v1}.
     *
     * <p>Ported verbatim from the v1 {@code MasterOutboxPollingScheduler}: event
     * types that are not {@code master.<aggregate>.<action>} are rejected with
     * {@link IllegalArgumentException} (the outbox is master-service-only). This
     * is defensive — the closed {@code DomainEvent} set only ever produces
     * {@code master.*} event types — but it keeps the topic mapping honest (F3).
     */
    private static TopicResolver topicResolver() {
        return MasterOutboxPublisher::topicFor;
    }

    /**
     * Topic-resolution helper. Exposed package-private (and static) so the unit
     * test can exercise the mapping + reject-unmapped behaviour directly.
     */
    static String topicFor(String eventType) {
        if (eventType == null || !eventType.startsWith("master.")) {
            throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
        String[] parts = eventType.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
        return TOPIC_PREFIX + parts[1] + TOPIC_SUFFIX;
    }
}
