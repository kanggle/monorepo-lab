package com.example.erp.notification.infrastructure.messaging;

import com.example.erp.notification.application.NotifyOnApprovalEventUseCase;
import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationCommand;
import com.example.erp.notification.domain.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Shared consume logic for the four approval-transition consumers: map →
 * dispatch (resolve recipient + render + persist + deliver + dedupe) → ACK.
 * An {@link InvalidEnvelopeException} is acked + routed to the DLT without
 * retry; any other exception propagates so {@code @RetryableTopic} retries then
 * routes to the DLT on exhaustion.
 */
@Slf4j
abstract class ApprovalEventConsumerSupport {

    private final NotifyOnApprovalEventUseCase useCase;
    private final EnvelopeToCommandMapper mapper;
    private final ConsumerMetrics metrics;

    protected ApprovalEventConsumerSupport(NotifyOnApprovalEventUseCase useCase,
                                           EnvelopeToCommandMapper mapper,
                                           ConsumerMetrics metrics) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    protected void process(ConsumerRecord<String, String> record, Acknowledgment ack,
                           String topic, NotificationType type) {
        try {
            NotifyOnApprovalCommand command = mapper.map(record.value(), topic, type);
            useCase.handle(command);
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            handleInvalidEnvelope(record, ack, topic, e);
        } catch (Exception e) {
            throw failed(record, topic, e);
        }
    }

    /**
     * Delegation consume path (TASK-ERP-BE-014). Same error handling as
     * {@link #process} (invalid envelope → DLT without retry; other → propagate so
     * {@code @RetryableTopic} retries then DLTs), but maps the delegation-shaped
     * envelope to a {@link NotifyOnDelegationCommand}.
     */
    protected void processDelegation(ConsumerRecord<String, String> record, Acknowledgment ack,
                                     String topic) {
        try {
            NotifyOnDelegationCommand command = mapper.mapDelegation(record.value(), topic);
            useCase.handle(command);
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            handleInvalidEnvelope(record, ack, topic, e);
        } catch (Exception e) {
            throw failed(record, topic, e);
        }
    }

    private void handleInvalidEnvelope(ConsumerRecord<String, String> record, Acknowledgment ack,
                                       String topic, InvalidEnvelopeException e) {
        log.error("Invalid envelope on topic={} offset={}; routing to DLT: {}",
                record.topic(), record.offset(), e.getMessage());
        metrics.dlt(topic);
        ack.acknowledge();
        throw e;
    }

    private RuntimeException failed(ConsumerRecord<String, String> record, String topic, Exception e) {
        log.error("Failed to process {}: partition={} offset={} error={}",
                topic, record.partition(), record.offset(), e.getMessage(), e);
        return new RuntimeException("Failed to process " + topic + " event", e);
    }
}
