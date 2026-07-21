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
 * Consumes {@code erp.masterdata.department.changed.v1} → upserts
 * {@code department_proj} (PARENT_MOVED upserts the new parentId; RETIRED marks).
 * Manual ACK; 3 retries (1s, 2s) → {@code .DLT}; invalid envelope → immediate
 * DLT (no retry — {@link InvalidEnvelopeException} excluded from retry). The
 * consume→validate→dispatch→(retry/DLT) body lives in
 * {@link AbstractMasterChangeConsumer}.
 */
@Component
public class DepartmentChangedConsumer extends AbstractMasterChangeConsumer {

    static final String TOPIC = "erp.masterdata.department.changed.v1";

    public DepartmentChangedConsumer(ApplyMasterChangeUseCase useCase,
                                     EnvelopeToCommandMapper mapper,
                                     ConsumerMetrics metrics) {
        super(mapper, metrics, TOPIC, "department", "department.changed", useCase::applyDepartment);
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
