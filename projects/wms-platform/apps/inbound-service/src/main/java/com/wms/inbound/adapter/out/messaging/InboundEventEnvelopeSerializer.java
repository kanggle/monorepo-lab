package com.wms.inbound.adapter.out.messaging;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.domain.event.AsnCancelledEvent;
import com.wms.inbound.domain.event.AsnClosedEvent;
import com.wms.inbound.domain.event.AsnReceivedEvent;
import com.wms.inbound.domain.event.InboundDomainEvent;
import com.wms.inbound.domain.event.InspectionCompletedEvent;
import com.wms.inbound.domain.event.PutawayCompletedEvent;
import com.wms.inbound.domain.event.PutawayInstructedEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class InboundEventEnvelopeSerializer {

    private static final String PRODUCER = "inbound-service";
    private static final String EVENT_VERSION = "v1";

    private final ObjectMapper objectMapper;

    public InboundEventEnvelopeSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Serialised serialise(InboundDomainEvent event) {
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
            throw new IllegalStateException("Failed to serialise inbound event envelope", e);
        }
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? null : traceId;
    }

    private static Object buildPayload(InboundDomainEvent event) {
        return switch (event) {
            case AsnReceivedEvent e -> receivedPayload(e);
            case AsnCancelledEvent e -> cancelledPayload(e);
            case InspectionCompletedEvent e -> inspectionCompletedPayload(e);
            case PutawayInstructedEvent e -> putawayInstructedPayload(e);
            case PutawayCompletedEvent e -> putawayCompletedPayload(e);
            case AsnClosedEvent e -> asnClosedPayload(e);
        };
    }

    private static Map<String, Object> putawayInstructedPayload(PutawayInstructedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("putawayInstructionId", e.putawayInstructionId().toString());
        p.put("asnId", e.asnId().toString());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("plannedBy", e.plannedBy());
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("putawayLineId", l.putawayLineId().toString());
            lm.put("asnLineId", l.asnLineId().toString());
            lm.put("skuId", l.skuId().toString());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("destinationLocationId", l.destinationLocationId().toString());
            lm.put("destinationLocationCode", l.destinationLocationCode());
            lm.put("qtyToPutaway", l.qtyToPutaway());
            return lm;
        }).toList();
        p.put("lines", lines);
        return p;
    }

    private static Map<String, Object> putawayCompletedPayload(PutawayCompletedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("putawayInstructionId", e.putawayInstructionId().toString());
        p.put("asnId", e.asnId().toString());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("completedAt", e.completedAt() != null ? e.completedAt().toString() : null);
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("putawayConfirmationId", l.putawayConfirmationId().toString());
            lm.put("skuId", l.skuId().toString());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("locationId", l.locationId().toString());
            lm.put("qtyReceived", l.qtyReceived());
            return lm;
        }).toList();
        p.put("lines", lines);
        return p;
    }

    private static Map<String, Object> asnClosedPayload(AsnClosedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("asnId", e.asnId().toString());
        p.put("asnNo", e.asnNo());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("closedAt", e.closedAt().toString());
        p.put("closedBy", e.closedBy());
        Map<String, Object> sm = new LinkedHashMap<>();
        sm.put("expectedTotal", e.summary().expectedTotal());
        sm.put("passedTotal", e.summary().passedTotal());
        sm.put("damagedTotal", e.summary().damagedTotal());
        sm.put("shortTotal", e.summary().shortTotal());
        sm.put("putawayConfirmedTotal", e.summary().putawayConfirmedTotal());
        sm.put("discrepancyCount", e.summary().discrepancyCount());
        p.put("summary", sm);
        return p;
    }

    private static Map<String, Object> receivedPayload(AsnReceivedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("asnId", e.asnId().toString());
        p.put("asnNo", e.asnNo());
        p.put("source", e.source());
        p.put("supplierPartnerId", e.supplierPartnerId().toString());
        p.put("supplierPartnerCode", e.supplierPartnerCode());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("expectedArriveDate", e.expectedArriveDate() != null ? e.expectedArriveDate().toString() : null);
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("asnLineId", l.asnLineId().toString());
            lm.put("lineNo", l.lineNo());
            lm.put("skuId", l.skuId().toString());
            lm.put("skuCode", l.skuCode());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("expectedQty", l.expectedQty());
            return lm;
        }).toList();
        p.put("lines", lines);
        return p;
    }

    private static Map<String, Object> cancelledPayload(AsnCancelledEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("asnId", e.asnId().toString());
        p.put("asnNo", e.asnNo());
        p.put("previousStatus", e.previousStatus());
        p.put("reason", e.reason());
        p.put("cancelledAt", e.cancelledAt().toString());
        return p;
    }

    private static Map<String, Object> inspectionCompletedPayload(InspectionCompletedEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("inspectionId", e.inspectionId().toString());
        p.put("asnId", e.asnId().toString());
        p.put("asnNo", e.asnNo());
        p.put("warehouseId", e.warehouseId().toString());
        p.put("inspectorId", e.inspectorId());
        p.put("completedAt", e.completedAt() != null ? e.completedAt().toString() : null);
        List<Map<String, Object>> lines = e.lines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("inspectionLineId", l.inspectionLineId().toString());
            lm.put("asnLineId", l.asnLineId().toString());
            lm.put("skuId", l.skuId().toString());
            lm.put("lotId", l.lotId() != null ? l.lotId().toString() : null);
            lm.put("lotNo", l.lotNo());
            lm.put("expectedQty", l.expectedQty());
            lm.put("qtyPassed", l.qtyPassed());
            lm.put("qtyDamaged", l.qtyDamaged());
            lm.put("qtyShort", l.qtyShort());
            return lm;
        }).toList();
        p.put("lines", lines);
        p.put("discrepancyCount", e.discrepancyCount());
        List<Map<String, Object>> discSummary = e.discrepancySummary().stream().map(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("discrepancyId", d.discrepancyId().toString());
            dm.put("asnLineId", d.asnLineId().toString());
            dm.put("discrepancyType", d.discrepancyType());
            dm.put("variance", d.variance());
            dm.put("acknowledged", d.acknowledged());
            return dm;
        }).toList();
        p.put("discrepancySummary", discSummary);
        return p;
    }

    public record Serialised(UUID eventId, String eventVersion, String json) {}
}
