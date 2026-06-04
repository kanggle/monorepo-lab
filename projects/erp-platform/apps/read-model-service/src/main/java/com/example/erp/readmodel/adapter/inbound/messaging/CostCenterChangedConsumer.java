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
 * Consumes {@code erp.masterdata.costcenter.changed.v1} → upserts
 * {@code cost_center_proj} (RETIRED marks). Manual ACK; 3 retries → {@code .DLT};
 * invalid envelope → immediate DLT (no retry).
 */
@Slf4j
@Component
public class CostCenterChangedConsumer {

    static final String TOPIC = "erp.masterdata.costcenter.changed.v1";

    private final ApplyMasterChangeUseCase useCase;
    private final EnvelopeToCommandMapper mapper;
    private final ConsumerMetrics metrics;

    public CostCenterChangedConsumer(ApplyMasterChangeUseCase useCase,
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
            useCase.applyCostCenter(cmd);
            metrics.applied("costcenter", cmd.changeKind().name());
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            log.error("Invalid envelope on topic={} offset={}; routing to DLT: {}",
                    record.topic(), record.offset(), e.getMessage());
            metrics.dlt(TOPIC);
            ack.acknowledge();
            throw e;
        } catch (Exception e) {
            log.error("Failed to process costcenter.changed: partition={} offset={} error={}",
                    record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process costcenter.changed event", e);
        }
    }
}
