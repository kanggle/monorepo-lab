package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.ApplyApprovalFactUseCase;
import com.example.erp.readmodel.application.command.ApprovalFactCommand;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes the four {@code erp.approval.{submitted,approved,rejected,withdrawn}.v1}
 * topics → upserts the {@code approval_fact_proj} latest-fact projection
 * (TASK-ERP-BE-010). The handlers join the EXISTING {@code erp-read-model-v1}
 * consumer group (partition key = {@code approvalRequestId}), reuse the
 * {@code processed_events} dedupe (T8) + {@code @RetryableTopic} 3-retry + DLT
 * resilience (ADR-MONO-005 Category C) — identical shape to the masterdata
 * consumers. Invalid envelope → immediate DLT (no retry —
 * {@link InvalidEnvelopeException} excluded).
 *
 * <p>One consumer with four {@code @KafkaListener} methods (the status is bound
 * to the topic, NOT trusted from the payload). Read-only / no re-emission /
 * no write-back (E5 terminal).
 */
@Slf4j
@Component
public class ApprovalEventConsumer {

    static final String TOPIC_SUBMITTED = "erp.approval.submitted.v1";
    static final String TOPIC_APPROVED = "erp.approval.approved.v1";
    static final String TOPIC_REJECTED = "erp.approval.rejected.v1";
    static final String TOPIC_WITHDRAWN = "erp.approval.withdrawn.v1";

    private static final String METRIC_AGGREGATE = "approval";

    private final ApplyApprovalFactUseCase useCase;
    private final ApprovalEnvelopeToCommandMapper mapper;
    private final ConsumerMetrics metrics;

    public ApprovalEventConsumer(ApplyApprovalFactUseCase useCase,
                                 ApprovalEnvelopeToCommandMapper mapper,
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
    @KafkaListener(topics = TOPIC_SUBMITTED, groupId = "erp-read-model-v1")
    public void onSubmitted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_SUBMITTED, ApprovalStatus.SUBMITTED);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC_APPROVED, groupId = "erp-read-model-v1")
    public void onApproved(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_APPROVED, ApprovalStatus.APPROVED);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC_REJECTED, groupId = "erp-read-model-v1")
    public void onRejected(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_REJECTED, ApprovalStatus.REJECTED);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC_WITHDRAWN, groupId = "erp-read-model-v1")
    public void onWithdrawn(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_WITHDRAWN, ApprovalStatus.WITHDRAWN);
    }

    private void handle(ConsumerRecord<String, String> record, Acknowledgment ack,
                        String topic, ApprovalStatus status) {
        try {
            ApprovalFactCommand cmd = mapper.map(record.value(), topic, status);
            useCase.apply(cmd);
            metrics.applied(METRIC_AGGREGATE, status.name());
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            log.error("Invalid approval envelope on topic={} offset={}; routing to DLT: {}",
                    record.topic(), record.offset(), e.getMessage());
            metrics.dlt(topic);
            ack.acknowledge();
            throw e;
        } catch (Exception e) {
            log.error("Failed to process {}: partition={} offset={} error={}",
                    topic, record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process approval event on " + topic, e);
        }
    }
}
