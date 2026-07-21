package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.MasterChangeCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;

import java.util.function.Consumer;

/**
 * Shared consume→validate→dispatch→(retry/DLT) skeleton for the 4 masterdata
 * read-model consumers (cost-center / department / employee / job-grade), which
 * differ only in the topic, the projection dispatch, and the aggregate/event
 * labels (TASK-ERP-BE-034). Each concrete consumer keeps its own
 * {@code @RetryableTopic}/{@code @KafkaListener}-annotated {@code consume(...)}
 * method — so the resilience config stays byte-identical and per-listener — and
 * delegates the body to {@link #handle}.
 *
 * <p>Behavior is preserved exactly: manual ACK placement, the two-tier try/catch
 * (invalid-envelope → immediate DLT + rethrow; generic failure →
 * rethrow-as-{@link RuntimeException} for retry), the log messages, and the
 * emitted metrics are unchanged. The logger is bound to the concrete subclass
 * ({@code getClass()}) so the log category matches each pre-refactor per-consumer
 * {@code @Slf4j} logger.
 */
abstract class AbstractMasterChangeConsumer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final EnvelopeToCommandMapper mapper;
    private final ConsumerMetrics metrics;
    private final String topic;
    private final String aggregate;
    private final String eventName;
    private final Consumer<MasterChangeCommand> dispatch;

    protected AbstractMasterChangeConsumer(EnvelopeToCommandMapper mapper,
                                           ConsumerMetrics metrics,
                                           String topic,
                                           String aggregate,
                                           String eventName,
                                           Consumer<MasterChangeCommand> dispatch) {
        this.mapper = mapper;
        this.metrics = metrics;
        this.topic = topic;
        this.aggregate = aggregate;
        this.eventName = eventName;
        this.dispatch = dispatch;
    }

    protected final void handle(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            MasterChangeCommand cmd = mapper.map(record.value(), topic);
            dispatch.accept(cmd);
            metrics.applied(aggregate, cmd.changeKind().name());
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            log.error("Invalid envelope on topic={} offset={}; routing to DLT: {}",
                    record.topic(), record.offset(), e.getMessage());
            metrics.dlt(topic);
            ack.acknowledge();
            throw e;
        } catch (Exception e) {
            log.error("Failed to process {}: partition={} offset={} error={}",
                    eventName, record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process " + eventName + " event", e);
        }
    }
}
