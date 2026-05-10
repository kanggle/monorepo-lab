package com.wms.outbound.adapter.in.messaging.consumer;

import com.wms.outbound.application.port.out.EventDedupePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single dispatcher bean for every {@code wms.master.*.v1} topic.
 *
 * <p>Six {@code @KafkaListener} methods (one per master aggregate)
 * delegate to the matching {@link MasterEventProjector} via the private
 * {@link #dispatch(String, MasterEventProjector, String, String)} helper.
 *
 * <h2>Transactional boundary</h2>
 *
 * <p>{@code @Transactional} sits on each listener method (the public,
 * proxied entry-point invoked by the Kafka container). The private
 * {@code dispatch} helper runs inside that TX without proxying — no Spring
 * AOP self-invocation hazard.
 *
 * <p>This intentionally departs from a base-class template. PR #304 (admin)
 * showed that pushing {@code @Transactional} onto a final template method
 * on an abstract base class breaks Awaitility ITs through self-invocation
 * timing edge cases. The dispatcher pattern here keeps the proxy boundary
 * on the concrete listener method exactly as the per-topic consumers had
 * it before consolidation.
 */
@Component
@Profile("!standalone")
public class MasterEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterEventConsumer.class);

    private final EventEnvelopeParser parser;
    private final EventDedupePort dedupe;
    private final MasterWarehouseProjector warehouseProjector;
    private final MasterZoneProjector zoneProjector;
    private final MasterLocationProjector locationProjector;
    private final MasterSkuProjector skuProjector;
    private final MasterPartnerProjector partnerProjector;
    private final MasterLotProjector lotProjector;

    public MasterEventConsumer(EventEnvelopeParser parser,
                               EventDedupePort dedupe,
                               MasterWarehouseProjector warehouseProjector,
                               MasterZoneProjector zoneProjector,
                               MasterLocationProjector locationProjector,
                               MasterSkuProjector skuProjector,
                               MasterPartnerProjector partnerProjector,
                               MasterLotProjector lotProjector) {
        this.parser = parser;
        this.dedupe = dedupe;
        this.warehouseProjector = warehouseProjector;
        this.zoneProjector = zoneProjector;
        this.locationProjector = locationProjector;
        this.skuProjector = skuProjector;
        this.partnerProjector = partnerProjector;
        this.lotProjector = lotProjector;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-warehouse:wms.master.warehouse.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onWarehouseEvent(@Payload String rawJson,
                                 @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        dispatch("master-warehouse", warehouseProjector, "master.warehouse", rawJson);
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-zone:wms.master.zone.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onZoneEvent(@Payload String rawJson,
                            @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        dispatch("master-zone", zoneProjector, "master.zone", rawJson);
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-location:wms.master.location.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onLocationEvent(@Payload String rawJson,
                                @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        dispatch("master-location", locationProjector, "master.location", rawJson);
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-sku:wms.master.sku.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onSkuEvent(@Payload String rawJson,
                           @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        dispatch("master-sku", skuProjector, "master.sku", rawJson);
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-partner:wms.master.partner.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onPartnerEvent(@Payload String rawJson,
                               @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        dispatch("master-partner", partnerProjector, "master.partner", rawJson);
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-lot:wms.master.lot.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onLotEvent(@Payload String rawJson,
                           @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        dispatch("master-lot", lotProjector, "master.lot", rawJson);
    }

    private void dispatch(String consumerName,
                          MasterEventProjector projector,
                          String eventTypeForLog,
                          String rawJson) {
        EventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", consumerName);
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> projector.apply(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("{} event {} already applied; skipping",
                        eventTypeForLog, envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }
}
