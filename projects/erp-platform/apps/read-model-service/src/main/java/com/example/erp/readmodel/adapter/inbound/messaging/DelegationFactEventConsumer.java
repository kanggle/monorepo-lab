package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.ApplyDelegationFactUseCase;
import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes the two delegation topics ({@code erp.approval.delegated.v1} grant +
 * {@code erp.approval.delegation.revoked.v1} revoke) → upserts the
 * {@code delegation_fact_proj} latest-fact projection (TASK-ERP-BE-015). The
 * handlers join the EXISTING {@code erp-read-model-v1} consumer group (partition
 * key = {@code grantId}), reuse the {@code processed_events} dedupe (T8) +
 * {@code @RetryableTopic} 3-retry + DLT resilience (ADR-MONO-005 Category C) —
 * identical shape to the approval-fact consumer. Invalid envelope → immediate
 * DLT (no retry — {@link InvalidEnvelopeException} excluded).
 *
 * <p>One consumer with two {@code @KafkaListener} methods (the status is bound to
 * the topic, NOT trusted from the payload): {@code delegated} → ACTIVE,
 * {@code revoked} → REVOKED. Read-only / no re-emission / no write-back (E5
 * terminal).
 */
@Slf4j
@Component
public class DelegationFactEventConsumer {

    static final String TOPIC_DELEGATED = "erp.approval.delegated.v1";
    static final String TOPIC_REVOKED = "erp.approval.delegation.revoked.v1";

    private static final String METRIC_AGGREGATE = "delegation";

    private final ApplyDelegationFactUseCase useCase;
    private final DelegationEnvelopeToCommandMapper mapper;
    private final ConsumerMetrics metrics;

    public DelegationFactEventConsumer(ApplyDelegationFactUseCase useCase,
                                       DelegationEnvelopeToCommandMapper mapper,
                                       ConsumerMetrics metrics) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC_DELEGATED, groupId = "erp-read-model-v1")
    public void onDelegated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_DELEGATED, DelegationFactStatus.ACTIVE);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC_REVOKED, groupId = "erp-read-model-v1")
    public void onRevoked(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_REVOKED, DelegationFactStatus.REVOKED);
    }

    private void handle(ConsumerRecord<String, String> record, Acknowledgment ack,
                        String topic, DelegationFactStatus status) {
        try {
            DelegationFactCommand cmd = mapper.map(record.value(), topic, status);
            useCase.apply(cmd);
            metrics.applied(METRIC_AGGREGATE, status.name());
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            log.error("Invalid delegation envelope on topic={} offset={}; routing to DLT: {}",
                    record.topic(), record.offset(), e.getMessage());
            metrics.dlt(topic);
            ack.acknowledge();
            throw e;
        } catch (Exception e) {
            log.error("Failed to process {}: partition={} offset={} error={}",
                    topic, record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process delegation event on " + topic, e);
        }
    }
}
