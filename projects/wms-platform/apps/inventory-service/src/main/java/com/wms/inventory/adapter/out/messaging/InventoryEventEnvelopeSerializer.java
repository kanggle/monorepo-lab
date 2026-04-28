package com.wms.inventory.adapter.out.messaging;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.domain.event.InventoryAdjustedEvent;
import com.wms.inventory.domain.event.InventoryConfirmedEvent;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryLowStockDetectedEvent;
import com.wms.inventory.domain.event.InventoryReceivedEvent;
import com.wms.inventory.domain.event.InventoryReleasedEvent;
import com.wms.inventory.domain.event.InventoryReservedEvent;
import com.wms.inventory.domain.event.InventoryTransferredEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Serialises a {@link InventoryDomainEvent} into the envelope JSON declared by
 * {@code specs/contracts/events/inventory-events.md} § Global Envelope, plus
 * the event-specific payload from each section.
 *
 * <p>The serialiser also generates the {@code eventId} (UUIDv7) so callers
 * don't have to. The id is returned alongside the JSON so the outbox writer
 * can persist it as the row PK — same id round-trips through Kafka so
 * downstream consumers dedupe correctly.
 */
@Component
public class InventoryEventEnvelopeSerializer {

    private static final String PRODUCER = "inventory-service";
    private static final String EVENT_VERSION = "v1";

    private final ObjectMapper objectMapper;

    public InventoryEventEnvelopeSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Serialised serialise(InventoryDomainEvent event) {
        UUID eventId = UuidV7.randomUuid();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", event.eventType());
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("aggregateType", event.aggregateType());
        envelope.put("aggregateId", event.aggregateId().toString());
        envelope.put("traceId", currentTraceId());
        envelope.put("actorId", event.actorId());
        envelope.put("payload", buildPayload(event));

        try {
            return new Serialised(eventId, EVENT_VERSION, objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise inventory event envelope", e);
        }
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? null : traceId;
    }

    private static Object buildPayload(InventoryDomainEvent event) {
        return switch (event) {
            case InventoryReceivedEvent e -> receivedPayload(e);
            case InventoryReservedEvent e -> reservedPayload(e);
            case InventoryReleasedEvent e -> releasedPayload(e);
            case InventoryConfirmedEvent e -> confirmedPayload(e);
            case InventoryAdjustedEvent e -> adjustedPayload(e);
            case InventoryTransferredEvent e -> transferredPayload(e);
            case InventoryLowStockDetectedEvent e -> lowStockPayload(e);
        };
    }

    private static Map<String, Object> receivedPayload(InventoryReceivedEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warehouseId", e.warehouseId().toString());
        payload.put("sourceEventId", e.sourceEventId() == null ? null : e.sourceEventId().toString());
        payload.put("asnId", e.asnId() == null ? null : e.asnId().toString());
        List<Map<String, Object>> lines = e.lines().stream().map(line -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("inventoryId", line.inventoryId().toString());
            l.put("locationId", line.locationId().toString());
            l.put("locationCode", line.locationCode());
            l.put("skuId", line.skuId().toString());
            l.put("lotId", line.lotId() == null ? null : line.lotId().toString());
            l.put("qtyReceived", line.qtyReceived());
            l.put("availableQtyAfter", line.availableQtyAfter());
            return l;
        }).toList();
        payload.put("lines", lines);
        return payload;
    }

    private static Map<String, Object> reservedPayload(InventoryReservedEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", e.reservationId().toString());
        payload.put("pickingRequestId", e.pickingRequestId().toString());
        payload.put("warehouseId", e.warehouseId().toString());
        payload.put("expiresAt", e.expiresAt().toString());
        List<Map<String, Object>> lines = e.lines().stream().map(line -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("reservationLineId", line.reservationLineId().toString());
            l.put("inventoryId", line.inventoryId().toString());
            l.put("locationId", line.locationId().toString());
            l.put("skuId", line.skuId().toString());
            l.put("lotId", line.lotId() == null ? null : line.lotId().toString());
            l.put("quantity", line.quantity());
            l.put("availableQtyAfter", line.availableQtyAfter());
            l.put("reservedQtyAfter", line.reservedQtyAfter());
            return l;
        }).toList();
        payload.put("lines", lines);
        return payload;
    }

    private static Map<String, Object> releasedPayload(InventoryReleasedEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", e.reservationId().toString());
        payload.put("pickingRequestId", e.pickingRequestId().toString());
        payload.put("warehouseId", e.warehouseId().toString());
        payload.put("releasedReason", e.releasedReason().name());
        List<Map<String, Object>> lines = e.lines().stream().map(line -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("reservationLineId", line.reservationLineId().toString());
            l.put("inventoryId", line.inventoryId().toString());
            l.put("locationId", line.locationId().toString());
            l.put("skuId", line.skuId().toString());
            l.put("lotId", line.lotId() == null ? null : line.lotId().toString());
            l.put("quantity", line.quantity());
            l.put("availableQtyAfter", line.availableQtyAfter());
            l.put("reservedQtyAfter", line.reservedQtyAfter());
            return l;
        }).toList();
        payload.put("lines", lines);
        payload.put("releasedAt", e.releasedAt().toString());
        return payload;
    }

    private static Map<String, Object> confirmedPayload(InventoryConfirmedEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", e.reservationId().toString());
        payload.put("pickingRequestId", e.pickingRequestId().toString());
        payload.put("warehouseId", e.warehouseId().toString());
        List<Map<String, Object>> lines = e.lines().stream().map(line -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("reservationLineId", line.reservationLineId().toString());
            l.put("inventoryId", line.inventoryId().toString());
            l.put("locationId", line.locationId().toString());
            l.put("skuId", line.skuId().toString());
            l.put("lotId", line.lotId() == null ? null : line.lotId().toString());
            l.put("quantity", line.quantity());
            l.put("reservedQtyAfter", line.reservedQtyAfter());
            return l;
        }).toList();
        payload.put("lines", lines);
        payload.put("confirmedAt", e.confirmedAt().toString());
        return payload;
    }

    private static Map<String, Object> adjustedPayload(InventoryAdjustedEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("adjustmentId", e.adjustmentId().toString());
        payload.put("inventoryId", e.inventoryId().toString());
        payload.put("locationId", e.locationId().toString());
        payload.put("skuId", e.skuId().toString());
        payload.put("lotId", e.lotId() == null ? null : e.lotId().toString());
        payload.put("bucket", e.bucket().name());
        payload.put("delta", e.delta());
        payload.put("reasonCode", e.reasonCode().name());
        payload.put("reasonNote", e.reasonNote());
        payload.put("movementType", e.movementType().name());
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("availableQty", e.inventory().availableQty());
        inv.put("reservedQty", e.inventory().reservedQty());
        inv.put("damagedQty", e.inventory().damagedQty());
        inv.put("onHandQty", e.inventory().onHandQty());
        inv.put("version", e.inventory().version());
        payload.put("inventory", inv);
        return payload;
    }

    private static Map<String, Object> transferredPayload(InventoryTransferredEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", e.transferId().toString());
        payload.put("warehouseId", e.warehouseId().toString());
        payload.put("skuId", e.skuId().toString());
        payload.put("lotId", e.lotId() == null ? null : e.lotId().toString());
        payload.put("quantity", e.quantity());
        payload.put("reasonCode", e.reasonCode().name());
        payload.put("source", endpointMap(e.source()));
        payload.put("target", endpointMap(e.target()));
        return payload;
    }

    private static Map<String, Object> endpointMap(InventoryTransferredEvent.Endpoint endpoint) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("locationId", endpoint.locationId().toString());
        map.put("locationCode", endpoint.locationCode());
        map.put("inventoryId", endpoint.inventoryId().toString());
        map.put("availableQtyAfter", endpoint.availableQtyAfter());
        map.put("wasCreated", endpoint.wasCreated());
        return map;
    }

    private static Map<String, Object> lowStockPayload(InventoryLowStockDetectedEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inventoryId", e.inventoryId().toString());
        payload.put("locationId", e.locationId().toString());
        payload.put("locationCode", e.locationCode());
        payload.put("skuId", e.skuId().toString());
        payload.put("skuCode", e.skuCode());
        payload.put("lotId", e.lotId() == null ? null : e.lotId().toString());
        payload.put("availableQty", e.availableQty());
        payload.put("threshold", e.threshold());
        payload.put("triggeringEventType", e.triggeringEventType());
        payload.put("triggeringEventId", e.triggeringEventId() == null ? null : e.triggeringEventId().toString());
        return payload;
    }

    /** Serialised envelope plus the generated {@code eventId}. */
    public record Serialised(UUID eventId, String eventVersion, String json) {
    }
}
