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
 * Consumes {@code erp.approval.withdrawn.v1} → notify the <b>approver</b> (the
 * submitter withdrew their own pending request; the approver who had it pending
 * is told it no longer awaits their action). Manual ACK; 3 retries → {@code .DLT};
 * invalid envelope → immediate DLT.
 */
@Component
public class ApprovalWithdrawnConsumer extends ApprovalEventConsumerSupport {

    static final String TOPIC = "erp.approval.withdrawn.v1";

    public ApprovalWithdrawnConsumer(NotifyOnApprovalEventUseCase useCase,
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
        process(record, ack, TOPIC, NotificationType.APPROVAL_WITHDRAWN);
    }
}
