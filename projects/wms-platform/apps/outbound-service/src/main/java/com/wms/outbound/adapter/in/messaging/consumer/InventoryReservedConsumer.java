package com.wms.outbound.adapter.in.messaging.consumer;

import com.example.messaging.dedupe.EventDedupePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.command.RecordReservedPickingCommand;
import com.wms.outbound.application.port.in.RecordReservedPickingUseCase;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.inventory.reserved.v1}, advances the matching saga to
 * {@code RESERVED} via {@link OutboundSagaCoordinator}, and records the
 * {@code PickingRequest} inventory just reserved against.
 *
 * <p><b>The second half arrived with TASK-BE-586 / ADR-MONO-066 (option B).</b>
 * Before it, this consumer only moved the saga, nothing in production ever
 * created a picking request, and every outbound order stopped dead at
 * {@code PICKING}. The location ids are already on this event — inventory
 * assigns them while reserving — so recording them here needs no new domain
 * logic and creates no second, competing assignment.
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
    private final RecordReservedPickingUseCase recordReservedPicking;

    public InventoryReservedConsumer(InventoryConsumerSupport support,
                                     OutboundSagaCoordinator coordinator,
                                     RecordReservedPickingUseCase recordReservedPicking) {
        this.support = support;
        this.coordinator = coordinator;
        this.recordReservedPicking = recordReservedPicking;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-reserved:wms.inventory.reserved.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        support.dispatchWithEnvelope("inventory-reserved", "inventory.reserved", rawJson,
                (sagaId, envelope) -> {
                    coordinator.onInventoryReserved(sagaId);
                    recordReservedPicking.recordReservedPicking(
                            toCommand(sagaId, envelope.payload()));
                });
    }

    /**
     * Projects the payload onto the application-layer command. JSON shape stays
     * in the adapter; the service is testable without a parser.
     *
     * <p>Field names mirror {@code specs/contracts/events/inventory-events.md} §4.
     * A missing {@code lines} array yields an empty list, which the service
     * rejects against the order's line count rather than silently writing a
     * request with no lines.
     */
    private static RecordReservedPickingCommand toCommand(UUID sagaId, JsonNode payload) {
        List<RecordReservedPickingCommand.Line> lines = new ArrayList<>();
        JsonNode lineNodes = payload.path("lines");
        if (lineNodes.isArray()) {
            for (JsonNode l : lineNodes) {
                lines.add(new RecordReservedPickingCommand.Line(
                        uuid(l, "skuId"),
                        uuid(l, "lotId"),
                        uuid(l, "locationId"),
                        l.path("quantity").asInt()));
            }
        }
        return new RecordReservedPickingCommand(
                sagaId,
                uuid(payload, "pickingRequestId"),
                uuid(payload, "warehouseId"),
                lines);
    }

    private static UUID uuid(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return null;
        }
        return UUID.fromString(v.asText());
    }
}
