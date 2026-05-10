package com.wms.notification.adapter.inbound.messaging;

import com.wms.notification.application.port.inbound.ProcessInboundEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wms.notification.domain.alert.AlertEnvelope;

/**
 * Single bean hosting the 6 source-topic {@code @KafkaListener}s. Each
 * listener method shares envelope parsing, MDC enrichment, and the
 * dispatch-into-the-use-case shape via {@link #handle(String, String)}.
 *
 * <p>Co-locating the listener methods (instead of one subclass per topic)
 * removes ~150 LOC of byte-identical boilerplate while preserving the
 * per-topic Spring Kafka listener container — Spring creates one container
 * per {@code @KafkaListener} method, so per-topic concurrency tuning remains
 * available via {@code spring.kafka.listener.concurrency} or
 * {@code containerFactory} overrides.
 *
 * <p>Transaction shape: each listener method carries {@code @Transactional}
 * (default {@code REQUIRED} propagation). {@link #handle} is invoked in-bean
 * but the call is wrapped by the listener method's transaction; no
 * self-invocation trap because {@code handle} is not itself transactional.
 */
@Component
@Profile("!standalone")
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    static final String TOPIC_INVENTORY_ALERT = "wms.inventory.alert.v1";
    static final String TOPIC_INVENTORY_ADJUSTED = "wms.inventory.adjusted.v1";
    static final String TOPIC_INBOUND_INSPECTION_COMPLETED = "wms.inbound.inspection.completed.v1";
    static final String TOPIC_INBOUND_ASN_CANCELLED = "wms.inbound.asn.cancelled.v1";
    static final String TOPIC_OUTBOUND_ORDER_CANCELLED = "wms.outbound.order.cancelled.v1";
    static final String TOPIC_OUTBOUND_SHIPPING_CONFIRMED = "wms.outbound.shipping.confirmed.v1";

    private final AlertEnvelopeParser parser;
    private final ProcessInboundEventUseCase processUseCase;

    public AlertConsumer(AlertEnvelopeParser parser,
                         ProcessInboundEventUseCase processUseCase) {
        this.parser = parser;
        this.processUseCase = processUseCase;
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.inventory-alert:" + TOPIC_INVENTORY_ALERT + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onInventoryAlert(@Payload String rawJson) {
        handle(rawJson, TOPIC_INVENTORY_ALERT);
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.inventory-adjusted:" + TOPIC_INVENTORY_ADJUSTED + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onInventoryAdjusted(@Payload String rawJson) {
        handle(rawJson, TOPIC_INVENTORY_ADJUSTED);
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.inbound-inspection-completed:" + TOPIC_INBOUND_INSPECTION_COMPLETED + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onInboundInspectionCompleted(@Payload String rawJson) {
        handle(rawJson, TOPIC_INBOUND_INSPECTION_COMPLETED);
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.inbound-asn-cancelled:" + TOPIC_INBOUND_ASN_CANCELLED + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onInboundAsnCancelled(@Payload String rawJson) {
        handle(rawJson, TOPIC_INBOUND_ASN_CANCELLED);
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.outbound-order-cancelled:" + TOPIC_OUTBOUND_ORDER_CANCELLED + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onOutboundOrderCancelled(@Payload String rawJson) {
        handle(rawJson, TOPIC_OUTBOUND_ORDER_CANCELLED);
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.outbound-shipping-confirmed:" + TOPIC_OUTBOUND_SHIPPING_CONFIRMED + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onOutboundShippingConfirmed(@Payload String rawJson) {
        handle(rawJson, TOPIC_OUTBOUND_SHIPPING_CONFIRMED);
    }

    /**
     * Parse the raw envelope and forward to the application service.
     * Exceptions bubble up so Spring Kafka's {@code DefaultErrorHandler}
     * can either retry transiently or route to DLT.
     */
    void handle(String rawJson, String sourceTopic) {
        AlertEnvelope envelope = parser.parse(rawJson, sourceTopic);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("sourceTopic", sourceTopic);
        try {
            ProcessInboundEventUseCase.Outcome outcome = processUseCase.process(envelope);
            log.debug("Processed eventId={} from topic={} outcome={}",
                    envelope.eventId(), sourceTopic, outcome);
        } finally {
            MDC.remove("eventId");
            MDC.remove("sourceTopic");
        }
    }
}
