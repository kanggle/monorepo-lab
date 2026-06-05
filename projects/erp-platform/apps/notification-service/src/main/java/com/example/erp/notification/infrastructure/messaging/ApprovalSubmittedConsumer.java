package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.NotifyOnApprovalEventUseCase;
import com.example.erp.notification.domain.notification.NotificationType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code erp.approval.submitted.v1} → notify the approver. Manual ACK;
 * 3 retries (1s, 2s) → {@code .DLT}; invalid envelope → immediate DLT
 * ({@link InvalidEnvelopeException} excluded from retry).
 */
@Component
public class ApprovalSubmittedConsumer extends ApprovalEventConsumerSupport {

    static final String TOPIC = "erp.approval.submitted.v1";

    public ApprovalSubmittedConsumer(NotifyOnApprovalEventUseCase useCase,
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
        process(record, ack, TOPIC, NotificationType.APPROVAL_SUBMITTED);
    }
}
