package com.wms.admin.infra.messaging;

import com.wms.admin.application.projection.OutboundProjectionService;
import com.wms.admin.application.projection.ProjectionEnvelope;
import com.wms.admin.application.projection.ProjectionEnvelopeParser;
import com.wms.admin.infra.observability.ProjectionMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the 2 outbound topics — {@code wms.outbound.order.v1},
 * {@code wms.outbound.shipping.confirmed.v1} — per
 * {@code admin-events.md § Consumed Events}.
 */
@Component
@Profile("!standalone")
public class OutboundProjectionConsumer {

    private final OutboundProjectionService projectionService;
    private final ProjectionEnvelopeParser parser;
    private final ProjectionMetrics metrics;

    public OutboundProjectionConsumer(OutboundProjectionService projectionService,
                                      ProjectionEnvelopeParser parser,
                                      ProjectionMetrics metrics) {
        this.projectionService = projectionService;
        this.parser = parser;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = {
                    "${admin.projection.kafka.topics.outbound-order:wms.outbound.order.v1}",
                    "${admin.projection.kafka.topics.outbound-shipping:wms.outbound.shipping.confirmed.v1}"
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
