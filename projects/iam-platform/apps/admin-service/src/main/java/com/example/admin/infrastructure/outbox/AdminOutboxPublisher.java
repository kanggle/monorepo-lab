package com.example.admin.infrastructure.outbox;

import com.example.admin.infrastructure.persistence.AdminOutboxJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOutboxJpaRepository;
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
 * admin-service outbox relay (TASK-BE-452 — outbox v1 → v2). A thin wrapper around
 * the shared {@link AbstractOutboxPublisher} ({@code libs/java-messaging},
 * ADR-MONO-004 § 5 — the {@code OutboxRow} path), mirroring finance account-service's
 * {@code AccountOutboxPublisher} + erp approval-service's {@code ApprovalOutboxPublisher}.
 * Replaces the v1 {@code AdminOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p><b>Two publishers, one table.</b> Both {@code OutboxAdminEventPublisher}
 * (admin.action.performed) and {@code OutboxTenantEventPublisher} (tenant.created /
 * suspended / reactivated / updated) write into this single {@code admin_outbox}
 * table, so {@link #topicFor} resolves BOTH publishers' event types (ported VERBATIM
 * from the v1 {@code AdminOutboxPollingScheduler.resolveTopic}).
 *
 * <p><b>No gate / plain metrics.</b> The v1 scheduler was an unconditional
 * {@code @Component} with no {@code @ConditionalOnProperty} and no custom failure
 * counter, so the relay adds neither — plain {@link MicrometerOutboxMetrics} +
 * {@code admin.outbox.pending.count} gauge. {@code @EnableScheduling} is already on
 * {@code AdminApplication}.
 *
 * <p>Topic resolution: iam topics are bare (no {@code .v1} suffix); each event maps to
 * its identically-named topic. An unmapped eventType is rejected.
 */
@Component
public class AdminOutboxPublisher extends AbstractOutboxPublisher<AdminOutboxJpaEntity> {

    static final String TOPIC_ACTION_PERFORMED = "admin.action.performed";
    static final String TOPIC_TENANT_CREATED = "tenant.created";
    static final String TOPIC_TENANT_SUSPENDED = "tenant.suspended";
    static final String TOPIC_TENANT_REACTIVATED = "tenant.reactivated";
    static final String TOPIC_TENANT_UPDATED = "tenant.updated";
    // TASK-BE-477 (ADR-MONO-045) — cross-org partnership lifecycle (bare iam topics).
    static final String TOPIC_PARTNERSHIP_INVITED = "partnership.invited";
    static final String TOPIC_PARTNERSHIP_ACCEPTED = "partnership.accepted";
    static final String TOPIC_PARTNERSHIP_SUSPENDED = "partnership.suspended";
    static final String TOPIC_PARTNERSHIP_REACTIVATED = "partnership.reactivated";
    static final String TOPIC_PARTNERSHIP_TERMINATED = "partnership.terminated";
    static final String TOPIC_PARTNERSHIP_PARTICIPANT_ADDED = "partnership.participant_added";
    static final String TOPIC_PARTNERSHIP_PARTICIPANT_REMOVED = "partnership.participant_removed";

    public AdminOutboxPublisher(AdminOutboxJpaRepository repository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                TransactionTemplate transactionTemplate,
                                Clock clock,
                                MeterRegistry meterRegistry,
                                @Value("${admin.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "admin"),
                clock,
                batchSize);

        Gauge.builder("admin.outbox.pending.count", repository,
                        AdminOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished admin outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${admin.outbox.poll-ms:1000}",
            initialDelayString = "${admin.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return AdminOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported VERBATIM from
     * the v1 {@code AdminOutboxPollingScheduler.resolveTopic} — covers BOTH the
     * {@code AdminEventPublisher} (admin.action.performed) AND the
     * {@code TenantEventPublisher} (tenant.created / suspended / reactivated /
     * updated) event types. Reject-unmapped preserved. Exposed package-private +
     * static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown admin event type: null");
        }
        return switch (eventType) {
            case "admin.action.performed" -> TOPIC_ACTION_PERFORMED;
            case "tenant.created" -> TOPIC_TENANT_CREATED;
            case "tenant.suspended" -> TOPIC_TENANT_SUSPENDED;
            case "tenant.reactivated" -> TOPIC_TENANT_REACTIVATED;
            case "tenant.updated" -> TOPIC_TENANT_UPDATED;
            // TASK-BE-477 (ADR-MONO-045) — the 7 partnership.* lifecycle types. An
            // unmapped type would throw at publish (invisible to Docker-free :check),
            // so all 7 MUST be present (caught by the cross-org-leak IT / relay test).
            case "partnership.invited" -> TOPIC_PARTNERSHIP_INVITED;
            case "partnership.accepted" -> TOPIC_PARTNERSHIP_ACCEPTED;
            case "partnership.suspended" -> TOPIC_PARTNERSHIP_SUSPENDED;
            case "partnership.reactivated" -> TOPIC_PARTNERSHIP_REACTIVATED;
            case "partnership.terminated" -> TOPIC_PARTNERSHIP_TERMINATED;
            case "partnership.participant_added" -> TOPIC_PARTNERSHIP_PARTICIPANT_ADDED;
            case "partnership.participant_removed" -> TOPIC_PARTNERSHIP_PARTICIPANT_REMOVED;
            default -> throw new IllegalArgumentException("Unknown admin event type: " + eventType);
        };
    }
}
