package com.example.account.infrastructure.outbox;

import com.example.account.infrastructure.persistence.AccountOutboxJpaEntity;
import com.example.account.infrastructure.persistence.AccountOutboxJpaRepository;
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
 * account-service outbox relay (TASK-BE-451 — outbox v1 → v2). A thin wrapper
 * around the shared {@link AbstractOutboxPublisher} ({@code libs/java-messaging},
 * ADR-MONO-004 § 5 — the {@code OutboxRow} path), mirroring finance
 * account-service's {@code AccountOutboxPublisher} + erp approval-service's
 * {@code ApprovalOutboxPublisher}. The poll loop, exponential backoff, Kafka send
 * and mark-as-published live in the lib; this class supplies the account specifics:
 * the {@code account_outbox} table via {@link AccountOutboxJpaRepository}, the topic
 * mapping, the {@code account} metric prefix, and the schedule cadence. Replaces the
 * v1 {@code AccountOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p><b>Two publishers, one table.</b> Both the
 * {@code OutboxAccountEventPublisher} (account.* lifecycle) and the
 * {@code OutboxTenantDomainSubscriptionEventPublisher}
 * (tenant.subscription.changed) write into this single {@code account_outbox}
 * table, so {@link #topicFor} resolves BOTH publishers' event types (ported
 * VERBATIM from the v1 {@code AccountOutboxPollingScheduler.resolveTopic}, incl.
 * TASK-BE-348's tenant.subscription.changed mapping).
 *
 * <p><b>No gate / plain metrics.</b> The v1 scheduler was an unconditional
 * {@code @Component} with no {@code @ConditionalOnProperty} and no custom failure
 * counter, so the relay adds neither — plain {@link MicrometerOutboxMetrics} +
 * {@code account.outbox.pending.count} gauge. {@code @EnableScheduling} is already
 * on {@code AccountApplication}.
 *
 * <p>Topic resolution: iam topics are bare (no {@code .v1} suffix); each event maps
 * to its identically-named topic. An unmapped eventType is rejected.
 */
@Component
public class AccountOutboxPublisher extends AbstractOutboxPublisher<AccountOutboxJpaEntity> {

    static final String TOPIC_CREATED = "account.created";
    static final String TOPIC_STATUS_CHANGED = "account.status.changed";
    static final String TOPIC_LOCKED = "account.locked";
    static final String TOPIC_UNLOCKED = "account.unlocked";
    static final String TOPIC_ROLES_CHANGED = "account.roles.changed";
    static final String TOPIC_DELETED = "account.deleted";
    static final String TOPIC_SUBSCRIPTION_CHANGED = "tenant.subscription.changed";

    public AccountOutboxPublisher(AccountOutboxJpaRepository repository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  TransactionTemplate transactionTemplate,
                                  Clock clock,
                                  MeterRegistry meterRegistry,
                                  @Value("${account.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "account"),
                clock,
                batchSize);

        Gauge.builder("account.outbox.pending.count", repository,
                        AccountOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished account outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${account.outbox.poll-ms:1000}",
            initialDelayString = "${account.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return AccountOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported VERBATIM from
     * the v1 {@code AccountOutboxPollingScheduler.resolveTopic} — covers BOTH the
     * account.* lifecycle events AND tenant.subscription.changed (TASK-BE-348).
     * Reject-unmapped preserved. Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown account event type: null");
        }
        return switch (eventType) {
            case "account.created" -> TOPIC_CREATED;
            case "account.status.changed" -> TOPIC_STATUS_CHANGED;
            case "account.locked" -> TOPIC_LOCKED;
            case "account.unlocked" -> TOPIC_UNLOCKED;
            case "account.roles.changed" -> TOPIC_ROLES_CHANGED;
            case "account.deleted" -> TOPIC_DELETED;
            case "tenant.subscription.changed" -> TOPIC_SUBSCRIPTION_CHANGED;
            default -> throw new IllegalArgumentException("Unknown account event type: " + eventType);
        };
    }
}
