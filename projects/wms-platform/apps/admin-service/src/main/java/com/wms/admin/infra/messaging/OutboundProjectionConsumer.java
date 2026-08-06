package com.wms.admin.infra.messaging;

import com.wms.admin.application.projection.OutboundProjectionService;
import com.wms.admin.application.projection.ProjectionEnvelopeParser;
import com.wms.admin.infra.observability.ProjectionMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes every topic {@code outbound-service} publishes and dispatches to
 * {@link OutboundProjectionService}.
 *
 * <p>Topic names follow {@code outbound-events.md § Topic Layout} — one topic
 * per event type, {@code wms.<eventType>.v1}. See
 * {@link InboundProjectionConsumer} for why this list must stay 1:1 with the
 * dispatched event types; the saga-flow event types
 * ({@code outbound.picking.*}, {@code outbound.packing.completed}) mutate no
 * read-model row but are still dispatched, so they belong here.
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
                    "${admin.projection.kafka.topics.outbound-order-received:wms.outbound.order.received.v1}",
                    "${admin.projection.kafka.topics.outbound-order-cancelled:wms.outbound.order.cancelled.v1}",
                    "${admin.projection.kafka.topics.outbound-picking-requested:wms.outbound.picking.requested.v1}",
                    "${admin.projection.kafka.topics.outbound-picking-cancelled:wms.outbound.picking.cancelled.v1}",
                    "${admin.projection.kafka.topics.outbound-picking-completed:wms.outbound.picking.completed.v1}",
                    "${admin.projection.kafka.topics.outbound-packing-completed:wms.outbound.packing.completed.v1}",
                    "${admin.projection.kafka.topics.outbound-shipping:wms.outbound.shipping.confirmed.v1}"
            },
            groupId = "${spring.kafka.consumer.group-id:admin-projection}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        ProjectionConsumerSupport.dispatch(record, parser, metrics, projectionService::project);
    }
}
