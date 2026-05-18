package com.example.finance.account.infrastructure.outbox;

import com.example.finance.account.application.event.AccountEventPublisher;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * account-service outbox relay. Inherits the polling loop from
 * {@code libs:java-messaging} and only declares the event-type → topic
 * mapping. {@code .v1} suffix follows the platform event versioning
 * convention (finance-account-events.md § Topics).
 *
 * <p>Disabled when {@code outbox.polling.enabled=false} — slice/unit runs use
 * this to avoid background polling without Kafka.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class AccountOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_ACCOUNT_OPENED = "finance.account.opened.v1";
    static final String TOPIC_ACCOUNT_KYC_UPGRADED = "finance.account.kyc.upgraded.v1";
    static final String TOPIC_ACCOUNT_STATUS_CHANGED = "finance.account.status.changed.v1";
    static final String TOPIC_BALANCE_HELD = "finance.balance.held.v1";
    static final String TOPIC_BALANCE_CAPTURED = "finance.balance.captured.v1";
    static final String TOPIC_BALANCE_RELEASED = "finance.balance.released.v1";
    static final String TOPIC_TRANSACTION_SETTLED = "finance.transaction.settled.v1";
    static final String TOPIC_TRANSACTION_COMPLETED = "finance.transaction.completed.v1";
    static final String TOPIC_TRANSACTION_FAILED = "finance.transaction.failed.v1";
    static final String TOPIC_TRANSACTION_REVERSED = "finance.transaction.reversed.v1";
    static final String TOPIC_COMPLIANCE_SANCTION_HIT = "finance.compliance.sanction.hit.v1";

    private final Counter publishFailures;

    public AccountOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                         KafkaTemplate<String, String> kafkaTemplate,
                                         MeterRegistry meterRegistry) {
        super(outboxPublisher, kafkaTemplate);
        this.publishFailures = Counter.builder("account_outbox_publish_failures_total")
                .description("Outbox events that failed to publish to Kafka.")
                .register(meterRegistry);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case AccountEventPublisher.EVENT_ACCOUNT_OPENED -> TOPIC_ACCOUNT_OPENED;
            case AccountEventPublisher.EVENT_ACCOUNT_KYC_UPGRADED -> TOPIC_ACCOUNT_KYC_UPGRADED;
            case AccountEventPublisher.EVENT_ACCOUNT_STATUS_CHANGED -> TOPIC_ACCOUNT_STATUS_CHANGED;
            case AccountEventPublisher.EVENT_BALANCE_HELD -> TOPIC_BALANCE_HELD;
            case AccountEventPublisher.EVENT_BALANCE_CAPTURED -> TOPIC_BALANCE_CAPTURED;
            case AccountEventPublisher.EVENT_BALANCE_RELEASED -> TOPIC_BALANCE_RELEASED;
            case AccountEventPublisher.EVENT_TRANSACTION_SETTLED -> TOPIC_TRANSACTION_SETTLED;
            case AccountEventPublisher.EVENT_TRANSACTION_COMPLETED -> TOPIC_TRANSACTION_COMPLETED;
            case AccountEventPublisher.EVENT_TRANSACTION_FAILED -> TOPIC_TRANSACTION_FAILED;
            case AccountEventPublisher.EVENT_TRANSACTION_REVERSED -> TOPIC_TRANSACTION_REVERSED;
            case AccountEventPublisher.EVENT_COMPLIANCE_SANCTION_HIT -> TOPIC_COMPLIANCE_SANCTION_HIT;
            default -> throw new IllegalArgumentException(
                    "Unknown account event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        publishFailures.increment();
    }
}
