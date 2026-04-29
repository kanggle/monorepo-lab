package com.wms.outbound.adapter.out.event.publisher;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.domain.event.OrderCancelledEvent;
import com.wms.outbound.domain.event.OrderReceivedEvent;
import com.wms.outbound.domain.event.OutboundDomainEvent;
import com.wms.outbound.domain.event.PackingCompletedEvent;
import com.wms.outbound.domain.event.PickingCancelledEvent;
import com.wms.outbound.domain.event.PickingCompletedEvent;
import com.wms.outbound.domain.event.PickingRequestedEvent;
import com.wms.outbound.domain.event.ShippingConfirmedEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Serialises {@link OutboundDomainEvent} instances into the canonical JSON
 * envelope shape declared in
 * {@code specs/contracts/events/outbound-events.md} § Global Envelope.
 *
 * <p>Pattern matching on the sealed event hierarchy keeps the per-event
 * payload mapping local and exhaustive.
 */
@Component
public class EventEnvelopeSerializer {

    private static final String PRODUCER = "outbound-service";
    private static final String EVENT_VERSION = "v1";

    private final ObjectMapper objectMapper;

    public EventEnvelopeSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Serialised serialise(OutboundDomainEvent event) {
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
            throw new IllegalStateException("Failed to serialise outbound event envelope", e);
        }
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? null : traceId;
    }

    private static Object buildPayload(OutboundDomainEvent event) {
        return switch (event) {
            case OrderReceivedEvent e -> orderReceivedPayload(e);
            case OrderCancelledEvent e -> orderCancelledPayload(e);
            case PickingRequestedEvent e -> pickingRequestedPayload(e);
            case PickingCancelledEvent e -> pickingCancelledPayload(e);
            case PickingCompletedEvent e -> pickingCompletedPayload(e);
            case PackingCompletedEvent e -> packingCompletedPayload(e);
            case ShippingConfirmedEvent e -> shippingConfirmedPayload(e);
        };
    }

    private static Map<String, Object> orderReceivedPayload(OrderReceivedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("orderId", e.orderId().toString());
        p.put("orderNo", e.orderNo());
        p.put("source", e.source());
        p.put("customerPartnerId", e.customerPartnerId().toString());
        p.put("customerPartnerCode", e.customerPartnerCode());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("requiredShipDate", e.requiredShipDate() != null ? e.requiredShipDate().toString() : null);
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("orderLineId", l.orderLineId().toString());
            lm.put("lineNo", l.lineNo());
            lm.put("skuId", l.skuId().toString());
            lm.put("skuCode", l.skuCode());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("qtyOrdered", l.qtyOrdered());
            return lm;
        }).toList();
        p.put("lines", lines);
        return p;
    }

    private static Map<String, Object> orderCancelledPayload(OrderCancelledEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("orderId", e.orderId().toString());
        p.put("orderNo", e.orderNo());
        p.put("previousStatus", e.previousStatus());
        p.put("reason", e.reason());
        p.put("cancelledAt", e.cancelledAt().toString());
        return p;
    }

    private static Map<String, Object> pickingRequestedPayload(PickingRequestedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("sagaId", e.sagaId().toString());
        p.put("reservationId", e.reservationId().toString());
        p.put("pickingRequestId", e.reservationId().toString());
        p.put("orderId", e.orderId().toString());
        p.put("warehouseId", e.warehouseId().toString());
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("orderLineId", l.orderLineId().toString());
            lm.put("skuId", l.skuId().toString());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("locationId", l.locationId() != null ? l.locationId().toString() : null);
            lm.put("qtyToReserve", l.qtyToReserve());
            return lm;
        }).toList();
        p.put("lines", lines);
        return p;
    }

    private static Map<String, Object> pickingCancelledPayload(PickingCancelledEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("sagaId", e.sagaId().toString());
        p.put("reservationId", e.reservationId().toString());
        p.put("pickingRequestId", e.reservationId().toString());
        p.put("orderId", e.orderId().toString());
        p.put("reason", e.reason());
        return p;
    }

    private static Map<String, Object> pickingCompletedPayload(PickingCompletedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("sagaId", e.sagaId().toString());
        p.put("orderId", e.orderId().toString());
        p.put("pickingConfirmationId", e.pickingConfirmationId().toString());
        p.put("confirmedBy", e.confirmedBy());
        p.put("confirmedAt", e.confirmedAt().toString());
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("orderLineId", l.orderLineId().toString());
            lm.put("skuId", l.skuId().toString());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("actualLocationId", l.actualLocationId().toString());
            lm.put("qtyConfirmed", l.qtyConfirmed());
            return lm;
        }).toList();
        p.put("lines", lines);
        return p;
    }

    private static Map<String, Object> packingCompletedPayload(PackingCompletedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("orderId", e.orderId().toString());
        p.put("orderNo", e.orderNo());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("completedAt", e.completedAt().toString());
        List<Map<String, Object>> units = e.packingUnits().stream().map(u -> {
            Map<String, Object> um = new LinkedHashMap<>();
            um.put("packingUnitId", u.packingUnitId().toString());
            um.put("cartonNo", u.cartonNo());
            um.put("packingType", u.packingType());
            um.put("weightGrams", u.weightGrams());
            um.put("lengthMm", u.lengthMm());
            um.put("widthMm", u.widthMm());
            um.put("heightMm", u.heightMm());
            List<Map<String, Object>> ulines = u.lines().stream().map(l -> {
                Map<String, Object> lm = new LinkedHashMap<>();
                lm.put("orderLineId", l.orderLineId().toString());
                lm.put("skuId", l.skuId().toString());
                lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
                lm.put("qty", l.qty());
                return lm;
            }).toList();
            um.put("lines", ulines);
            return um;
        }).toList();
        p.put("packingUnits", units);
        p.put("totalCartonCount", e.totalCartonCount());
        p.put("totalWeightGrams", e.totalWeightGrams());
        return p;
    }

    private static Map<String, Object> shippingConfirmedPayload(ShippingConfirmedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("sagaId", e.sagaId().toString());
        p.put("reservationId", e.reservationId().toString());
        p.put("orderId", e.orderId().toString());
        p.put("shipmentId", e.shipmentId().toString());
        p.put("shipmentNo", e.shipmentNo());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("shippedAt", e.shippedAt().toString());
        p.put("carrierCode", e.carrierCode());
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("orderLineId", l.orderLineId().toString());
            lm.put("skuId", l.skuId().toString());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("locationId", l.locationId() != null ? l.locationId().toString() : null);
            lm.put("qtyConfirmed", l.qtyConfirmed());
            return lm;
        }).toList();
        p.put("lines", lines);
        return p;
    }

    public record Serialised(UUID eventId, String eventVersion, String json) {}
}
