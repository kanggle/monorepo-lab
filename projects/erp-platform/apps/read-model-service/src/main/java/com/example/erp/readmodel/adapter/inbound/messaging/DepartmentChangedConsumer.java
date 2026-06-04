package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.ApplyMasterChangeUseCase;
import com.example.erp.readmodel.application.command.MasterChangeCommand;
import lombok.extern.slf4j.Slf4j;
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
 * DLT (no retry — {@link InvalidEnvelopeException} excluded from retry).
 */
@Slf4j
@Component
public class DepartmentChangedConsumer {

    static final String TOPIC = "erp.masterdata.department.changed.v1";

    private final ApplyMasterChangeUseCase useCase;
    private final EnvelopeToCommandMapper mapper;
    private final ConsumerMetrics metrics;

    public DepartmentChangedConsumer(ApplyMasterChangeUseCase useCase,
                                     EnvelopeToCommandMapper mapper,
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
    @KafkaListener(topics = TOPIC, groupId = "erp-read-model-v1")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            MasterChangeCommand cmd = mapper.map(record.value(), TOPIC);
            useCase.applyDepartment(cmd);
            metrics.applied("department", cmd.changeKind().name());
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            log.error("Invalid envelope on topic={} offset={}; routing to DLT: {}",
                    record.topic(), record.offset(), e.getMessage());
            metrics.dlt(TOPIC);
            ack.acknowledge();
            throw e;
        } catch (Exception e) {
            log.error("Failed to process department.changed: partition={} offset={} error={}",
                    record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process department.changed event", e);
        }
    }
}
