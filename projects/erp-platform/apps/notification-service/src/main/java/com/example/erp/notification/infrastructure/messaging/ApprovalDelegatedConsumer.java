package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.NotifyOnApprovalEventUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code erp.approval.delegated.v1} (TASK-ERP-BE-014) → notify the
 * delegate ("결재 권한 위임됨"). The fifth consumer; the delegation event has a
 * different aggregate / payload shape from the four transition events, so it uses
 * the parallel {@code processDelegation} path ({@code DelegationEvent} mapper) —
 * the four transition consumers are unchanged. Manual ACK; 3 retries (1s, 2s) →
 * {@code .DLT}; invalid envelope → immediate DLT
 * ({@link InvalidEnvelopeException} excluded from retry).
 */
@Component
public class ApprovalDelegatedConsumer extends ApprovalEventConsumerSupport {

    static final String TOPIC = "erp.approval.delegated.v1";

    public ApprovalDelegatedConsumer(NotifyOnApprovalEventUseCase useCase,
                                     EnvelopeToCommandMapper mapper,
                                     ConsumerMetrics metrics) {
        super(useCase, mapper, metrics);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC, groupId = "erp-notification-v1")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processDelegation(record, ack, TOPIC);
    }
}
