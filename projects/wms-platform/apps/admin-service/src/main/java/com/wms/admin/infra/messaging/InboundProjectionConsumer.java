package com.wms.admin.infra.messaging;

import com.wms.admin.application.projection.InboundProjectionService;
import com.wms.admin.application.projection.ProjectionEnvelope;
import com.wms.admin.application.projection.ProjectionEnvelopeParser;
import com.wms.admin.infra.observability.ProjectionMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the 3 inbound topics — {@code wms.inbound.asn.v1},
 * {@code wms.inbound.inspection.completed.v1},
 * {@code wms.inbound.putaway.completed.v1} — and dispatches to
 * {@link InboundProjectionService}. Topic naming follows
 * {@code admin-events.md § Consumed Events} (consumer-owned consolidated view
 * over the inbound producer's per-action split).
 */
@Component
@Profile("!standalone")
public class InboundProjectionConsumer {

    private final InboundProjectionService projectionService;
    private final ProjectionEnvelopeParser parser;
    private final ProjectionMetrics metrics;

    public InboundProjectionConsumer(InboundProjectionService projectionService,
                                     ProjectionEnvelopeParser parser,
                                     ProjectionMetrics metrics) {
        this.projectionService = projectionService;
        this.parser = parser;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = {
                    "${admin.projection.kafka.topics.inbound-asn:wms.inbound.asn.v1}",
                    "${admin.projection.kafka.topics.inbound-inspection:wms.inbound.inspection.completed.v1}",
                    "${admin.projection.kafka.topics.inbound-putaway:wms.inbound.putaway.completed.v1}"
            },
            groupId = "${spring.kafka.consumer.group-id:admin-projection}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        try {
            ProjectionEnvelope envelope = parser.parse(record.value(), topic);
            MDC.put("eventId", envelope.eventId().toString());
            MDC.put("sourceTopic", topic);
            try {
                projectionService.project(envelope);
            } finally {
                MDC.remove("eventId");
                MDC.remove("sourceTopic");
            }
        } catch (RuntimeException ex) {
            metrics.recordError(topic);
            throw ex;
        }
    }
}
