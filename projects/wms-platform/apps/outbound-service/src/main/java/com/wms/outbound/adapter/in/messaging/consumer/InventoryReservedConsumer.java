package com.wms.outbound.adapter.in.messaging.consumer;

import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.model.OutboundSaga;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.inventory.reserved.v1} and advances the matching saga
 * to {@code RESERVED} via {@link OutboundSagaCoordinator}.
 *
 * <p>Layered idempotency:
 * <ol>
 *   <li><b>Outer (eventId dedupe, T8).</b>
 *       {@link EventDedupePort#process} inserts a row into
 *       {@code outbound_event_dedupe} keyed by the envelope's
 *       {@code eventId}; a duplicate {@code eventId} short-circuits at the
 *       PK constraint.</li>
 *   <li><b>Inner (state-machine guard).</b> {@link OutboundSaga#onInventoryReserved}
 *       is a no-op if the saga is already {@code RESERVED}.</li>
 * </ol>
 *
 * <p>The consumer is {@code @Transactional} so the dedupe row, the saga
 * mutation, and any outbox writes commit atomically. The dedupe adapter
 * declares {@code Propagation.MANDATORY}; this method opens the outer TX.
 *
 * <p>Parse + dedupe + MDC scaffolding lives in
 * {@link InventoryConsumerSupport}.
 */
@Component
@Profile("!standalone")
public class InventoryReservedConsumer {

    private final InventoryConsumerSupport support;
    private final OutboundSagaCoordinator coordinator;

    public InventoryReservedConsumer(InventoryConsumerSupport support,
                                     OutboundSagaCoordinator coordinator) {
        this.support = support;
        this.coordinator = coordinator;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-reserved:wms.inventory.reserved.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        support.dispatch("inventory-reserved", "inventory.reserved", rawJson,
                coordinator::onInventoryReserved);
    }
}
