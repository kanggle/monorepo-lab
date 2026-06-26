package com.example.finance.account.infrastructure.outbox;

import com.example.finance.account.application.event.AccountEventPublisher;
import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * account-service outbox relay (TASK-FIN-BE-045 — outbox v1 → v2). Thin wrapper
 * around the shared {@link AbstractOutboxPublisher} ({@code libs/java-messaging},
 * ADR-MONO-004 § 5 — the {@code OutboxRow} path), mirroring ledger-service's
 * {@code LedgerOutboxPublisher}. The poll loop, exponential backoff, Kafka send
 * and mark-as-published live in the lib; this class supplies the account
 * specifics: the {@code account_outbox} table via {@link AccountOutboxJpaRepository},
 * the topic mapping, the {@code account} metric prefix, and the schedule cadence.
 *
 * <p>Topic resolution: the outbox row's {@code eventType} is the fully dotted
 * {@code finance.account.* / finance.balance.* / finance.transaction.* /
 * finance.compliance.*} name, so the topic is {@code <eventType>.v1} — preserving
 * the v1 {@code AccountOutboxPollingScheduler} mapping exactly. The v1 whitelist
 * is preserved: an unmapped eventType is rejected with
 * {@link IllegalArgumentException} (defensive — the closed publisher event set
 * only ever produces the 11 known types).
 *
 * <p>Background polling is gated off by {@code account.outbox.polling.enabled=false}
 * so slice/unit runs never start a Kafka-less scheduler; the integration suite
 * enables it. The gate defaults ON ({@code matchIfMissing}).
 */
@Component
@ConditionalOnProperty(value = "account.outbox.polling.enabled", havingValue = "true",
        matchIfMissing = true)
public class AccountOutboxPublisher extends AbstractOutboxPublisher<AccountOutboxJpaEntity> {

    /** The closed set of event types account-service emits (v1 whitelist, preserved). */
    private static final Set<String> KNOWN_EVENT_TYPES = Set.of(
            AccountEventPublisher.EVENT_ACCOUNT_OPENED,
            AccountEventPublisher.EVENT_ACCOUNT_KYC_UPGRADED,
            AccountEventPublisher.EVENT_ACCOUNT_STATUS_CHANGED,
            AccountEventPublisher.EVENT_BALANCE_HELD,
            AccountEventPublisher.EVENT_BALANCE_CAPTURED,
            AccountEventPublisher.EVENT_BALANCE_RELEASED,
            AccountEventPublisher.EVENT_TRANSACTION_SETTLED,
            AccountEventPublisher.EVENT_TRANSACTION_COMPLETED,
            AccountEventPublisher.EVENT_TRANSACTION_FAILED,
            AccountEventPublisher.EVENT_TRANSACTION_REVERSED,
            AccountEventPublisher.EVENT_COMPLIANCE_SANCTION_HIT);

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
    @Scheduled(fixedDelayString = "${account.outbox.polling-interval-ms:500}",
            initialDelayString = "${account.outbox.initial-delay-ms:2000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return AccountOutboxPublisher::topicFor;
    }

    /**
     * {@code finance.account.X → finance.account.X.v1}. Rejects an unmapped
     * eventType (preserving the v1 scheduler's reject-unknown whitelist).
     * Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null || !KNOWN_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("Unknown account event type: " + eventType);
        }
        return eventType + ".v1";
    }
}
