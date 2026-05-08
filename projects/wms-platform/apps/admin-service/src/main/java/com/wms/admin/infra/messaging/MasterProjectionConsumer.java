package com.wms.admin.infra.messaging;

import com.wms.admin.application.projection.MasterProjectionService;
import com.wms.admin.application.projection.ProjectionEnvelope;
import com.wms.admin.application.projection.ProjectionEnvelopeParser;
import com.wms.admin.infra.observability.ProjectionMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the 6 master topics — {@code wms.master.{warehouse|zone|location|
 * sku|partner|lot}.v1} — and dispatches to {@link MasterProjectionService}.
 *
 * <p>Single consumer-group {@code admin-projection} (production); test
 * profile overrides via {@code ${spring.kafka.consumer.group-id}}'s
 * {@code ${random.uuid}} suffix to prevent cross-class offset leak (TASK-MONO-046-3
 * SCM/security-service learning).
 *
 * <p>Disabled under the {@code standalone} profile so Flyway-only IT boots
 * without Kafka.
 */
@Component
@Profile("!standalone")
public class MasterProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterProjectionConsumer.class);

    private final MasterProjectionService projectionService;
    private final ProjectionEnvelopeParser parser;
    private final ProjectionMetrics metrics;

    public MasterProjectionConsumer(MasterProjectionService projectionService,
                                    ProjectionEnvelopeParser parser,
                                    ProjectionMetrics metrics) {
        this.projectionService = projectionService;
        this.parser = parser;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = {
                    "${admin.projection.kafka.topics.master-warehouse:wms.master.warehouse.v1}",
                    "${admin.projection.kafka.topics.master-zone:wms.master.zone.v1}",
                    "${admin.projection.kafka.topics.master-location:wms.master.location.v1}",
                    "${admin.projection.kafka.topics.master-sku:wms.master.sku.v1}",
                    "${admin.projection.kafka.topics.master-partner:wms.master.partner.v1}",
                    "${admin.projection.kafka.topics.master-lot:wms.master.lot.v1}"
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
