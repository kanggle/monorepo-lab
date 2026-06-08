package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.inventory.reserve.failed.v1} (inventory-service's
 * reservation-shortfall signal) and drives the saga
 * {@code REQUESTED → RESERVE_FAILED} via {@link OutboundSagaCoordinator}, which
 * also sets {@code Order → BACKORDERED} and emits {@code outbound.order.cancelled}
 * (reason carried through) — the cross-project backorder signal the ecommerce
 * side consumes (TASK-MONO-196, ADR-MONO-022 §D4).
 *
 * <p>The negative-branch counterpart of {@link InventoryReservedConsumer}.
 * Layered idempotency identical to its siblings: outer eventId dedupe (T8) +
 * the inner state-machine guard ({@code onReserveFailed} is a no-op once the
 * order has left {@code RECEIVED}/{@code PICKING}).
 *
 * <p>Uses {@link InventoryConsumerSupport#dispatchWithEnvelope} so the
 * {@code reason} payload field reaches the coordinator.
 */
@Component
@Profile("!standalone")
public class InventoryReserveFailedConsumer {

    private static final String DEFAULT_REASON = "INSUFFICIENT_STOCK";

    private final InventoryConsumerSupport support;
    private final OutboundSagaCoordinator coordinator;

    public InventoryReserveFailedConsumer(InventoryConsumerSupport support,
                                          OutboundSagaCoordinator coordinator) {
        this.support = support;
        this.coordinator = coordinator;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-reserve-failed:wms.inventory.reserve.failed.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        support.dispatchWithEnvelope("inventory-reserve-failed", "inventory.reserve.failed", rawJson,
                (sagaId, envelope) -> coordinator.onReserveFailed(sagaId, reasonOf(envelope)));
    }

    private static String reasonOf(EventEnvelope envelope) {
        JsonNode reason = envelope.payload().get("reason");
        return reason != null && !reason.isNull() && reason.isTextual() ? reason.asText() : DEFAULT_REASON;
    }
}
