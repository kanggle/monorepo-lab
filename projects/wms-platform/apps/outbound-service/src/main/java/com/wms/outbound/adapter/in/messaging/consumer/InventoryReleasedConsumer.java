package com.wms.outbound.adapter.in.messaging.consumer;

import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.inventory.released.v1} and finalises the saga's
 * cancellation path: {@code CANCELLATION_REQUESTED → CANCELLED} (or no-op
 * if already cancelled).
 *
 * <p>Layered idempotency identical to {@link InventoryReservedConsumer}.
 * Parse + dedupe + MDC scaffolding lives in
 * {@link InventoryConsumerSupport}.
 */
@Component
@Profile("!standalone")
public class InventoryReleasedConsumer {

    private final InventoryConsumerSupport support;
    private final OutboundSagaCoordinator coordinator;

    public InventoryReleasedConsumer(InventoryConsumerSupport support,
                                     OutboundSagaCoordinator coordinator) {
        this.support = support;
        this.coordinator = coordinator;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-released:wms.inventory.released.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        support.dispatch("inventory-released", "inventory.released", rawJson,
                coordinator::onInventoryReleased);
    }
}
