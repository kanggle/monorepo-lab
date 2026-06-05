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
 * Consumes {@code erp.approval.delegation.revoked.v1} (TASK-ERP-BE-016) → notify
 * the delegate that their delegated authority was revoked ("위임 권한 회수됨"). The
 * sixth consumer; the revoke event has no validity window (a separate render
 * model {@code DelegationRevokedEvent}), so it uses the parallel
 * {@code processDelegationRevoked} path — the four transition consumers and the
 * delegated consumer are unchanged. Manual ACK; 3 retries (1s, 2s) → {@code .DLT};
 * invalid envelope → immediate DLT ({@link InvalidEnvelopeException} excluded).
 */
@Component
public class ApprovalDelegationRevokedConsumer extends ApprovalEventConsumerSupport {

    static final String TOPIC = "erp.approval.delegation.revoked.v1";

    public ApprovalDelegationRevokedConsumer(NotifyOnApprovalEventUseCase useCase,
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
        processDelegationRevoked(record, ack, TOPIC);
    }
}
