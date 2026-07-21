package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.ApplyMasterChangeUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code erp.masterdata.costcenter.changed.v1} → upserts
 * {@code cost_center_proj} (RETIRED marks). Manual ACK; 3 retries → {@code .DLT};
 * invalid envelope → immediate DLT (no retry). The consume→validate→dispatch→
 * (retry/DLT) body lives in {@link AbstractMasterChangeConsumer}.
 */
@Component
public class CostCenterChangedConsumer extends AbstractMasterChangeConsumer {

    static final String TOPIC = "erp.masterdata.costcenter.changed.v1";

    public CostCenterChangedConsumer(ApplyMasterChangeUseCase useCase,
                                     EnvelopeToCommandMapper mapper,
                                     ConsumerMetrics metrics) {
        super(mapper, metrics, TOPIC, "costcenter", "costcenter.changed", useCase::applyCostCenter);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC, groupId = "erp-read-model-v1")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack);
    }
}
