package com.wms.admin.infra.messaging;

import com.wms.admin.application.projection.InboundProjectionService;
import com.wms.admin.application.projection.ProjectionEnvelopeParser;
import com.wms.admin.infra.observability.ProjectionMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes every topic {@code inbound-service} publishes and dispatches to
 * {@link InboundProjectionService}.
 *
 * <p>Topic names follow {@code inbound-events.md § Topic Layout}, which is the
 * authoritative producer-side contract: one topic per event type, named
 * {@code wms.<eventType>.v1} by the outbox relay's {@code TopicResolver}. The
 * subscription list must therefore stay 1:1 with the event types
 * {@link InboundProjectionService} dispatches — an event type handled by the
 * switch but absent here is projected by nobody, silently
 * ({@code TASK-BE-582}). {@code ProjectionTopicWiringTest} is the guard.
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
                    "${admin.projection.kafka.topics.inbound-asn-received:wms.inbound.asn.received.v1}",
                    "${admin.projection.kafka.topics.inbound-asn-cancelled:wms.inbound.asn.cancelled.v1}",
                    "${admin.projection.kafka.topics.inbound-asn-closed:wms.inbound.asn.closed.v1}",
                    "${admin.projection.kafka.topics.inbound-inspection:wms.inbound.inspection.completed.v1}",
                    "${admin.projection.kafka.topics.inbound-putaway-instructed:wms.inbound.putaway.instructed.v1}",
                    "${admin.projection.kafka.topics.inbound-putaway:wms.inbound.putaway.completed.v1}"
            },
            groupId = "${spring.kafka.consumer.group-id:admin-projection}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        ProjectionConsumerSupport.dispatch(record, parser, metrics, projectionService::project);
    }
}
