package com.wms.admin.infra.messaging;

import com.wms.admin.application.projection.InventoryProjectionService;
import com.wms.admin.application.projection.ProjectionEnvelope;
import com.wms.admin.application.projection.ProjectionEnvelopeParser;
import com.wms.admin.infra.observability.ProjectionMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the 7 inventory topics — {@code wms.inventory.{received|adjusted|
 * transferred|reserved|released|confirmed|alert}.v1} — per
 * {@code admin-events.md § Consumed Events}.
 */
@Component
@Profile("!standalone")
public class InventoryProjectionConsumer {

    private final InventoryProjectionService projectionService;
    private final ProjectionEnvelopeParser parser;
    private final ProjectionMetrics metrics;

    public InventoryProjectionConsumer(InventoryProjectionService projectionService,
                                       ProjectionEnvelopeParser parser,
                                       ProjectionMetrics metrics) {
        this.projectionService = projectionService;
        this.parser = parser;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = {
                    "${admin.projection.kafka.topics.inventory-received:wms.inventory.received.v1}",
                    "${admin.projection.kafka.topics.inventory-adjusted:wms.inventory.adjusted.v1}",
                    "${admin.projection.kafka.topics.inventory-transferred:wms.inventory.transferred.v1}",
                    "${admin.projection.kafka.topics.inventory-reserved:wms.inventory.reserved.v1}",
                    "${admin.projection.kafka.topics.inventory-released:wms.inventory.released.v1}",
                    "${admin.projection.kafka.topics.inventory-confirmed:wms.inventory.confirmed.v1}",
                    "${admin.projection.kafka.topics.inventory-alert:wms.inventory.alert.v1}"
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
