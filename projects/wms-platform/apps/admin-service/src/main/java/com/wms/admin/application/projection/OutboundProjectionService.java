package com.wms.admin.application.projection;

import static com.wms.admin.application.projection.PayloadJson.optionalDate;
import static com.wms.admin.application.projection.PayloadJson.optionalInstant;
import static com.wms.admin.application.projection.PayloadJson.optionalInteger;
import static com.wms.admin.application.projection.PayloadJson.optionalText;
import static com.wms.admin.application.projection.PayloadJson.optionalUuid;
import static com.wms.admin.application.projection.PayloadJson.text;
import static com.wms.admin.application.projection.PayloadJson.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.admin.application.repository.AdminEventDedupeRepository;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.master.PartnerRefEntity;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.outbound.OrderSummaryEntity;
import com.wms.admin.readmodel.outbound.OrderSummaryRepository;
import com.wms.admin.readmodel.outbound.ShipmentSummaryEntity;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Projects {@code wms.outbound.*} into:
 *
 * <ul>
 *   <li>{@code admin_order_summary} — from {@code outbound.order.received|cancelled}</li>
 *   <li>{@code admin_shipment_summary} — from {@code outbound.shipping.confirmed}</li>
 *   <li>{@code admin_throughput_outbound_daily} — incremented from
 *       {@code outbound.shipping.confirmed} (atomic counter)</li>
 *   <li>{@code admin_order_summary.shipped_at} — also stamped on shipping
 *       confirmation (cross-aggregate denorm).</li>
 * </ul>
 */
@Service
public class OutboundProjectionService extends AbstractProjectionService {

    private static final String SOURCE_SERVICE = "outbound";

    private final OrderSummaryRepository orderRepo;
    private final ShipmentSummaryRepository shipmentRepo;
    private final ThroughputOutboundDailyRepository throughputRepo;
    private final PartnerRefRepository partnerRepo;

    public OutboundProjectionService(OrderSummaryRepository orderRepo,
                                     ShipmentSummaryRepository shipmentRepo,
                                     ThroughputOutboundDailyRepository throughputRepo,
                                     PartnerRefRepository partnerRepo,
                                     AdminEventDedupeRepository dedupe,
                                     ProjectionMetrics metrics) {
        super(dedupe, metrics);
        this.orderRepo = orderRepo;
        this.shipmentRepo = shipmentRepo;
        this.throughputRepo = throughputRepo;
        this.partnerRepo = partnerRepo;
    }

    @Override
    protected String sourceService() {
        return SOURCE_SERVICE;
    }

    @Override
    protected DedupeOutcome dispatch(ProjectionEnvelope envelope) {
        switch (envelope.eventType()) {
            case "outbound.order.received":
                return onOrderReceived(envelope);
            case "outbound.order.cancelled":
                return onOrderCancelled(envelope);
            case "outbound.shipping.confirmed":
                return onShippingConfirmed(envelope);
            case "outbound.picking.requested":
            case "outbound.picking.cancelled":
            case "outbound.picking.completed":
            case "outbound.packing.completed":
                // These are saga-flow events; the v1 read-model surface only
                // tracks order/shipment summary, so we record APPLIED in
                // dedupe to suppress reprocessing but apply no row mutation.
                return DedupeOutcome.APPLIED;
            default:
                throw new UnknownEventTypeException(envelope.eventType(), envelope.sourceTopic());
        }
    }

    private DedupeOutcome onOrderReceived(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        UUID orderId = uuid(p, "orderId");
        Instant occurredAt = envelope.occurredAt();
        OrderSummaryEntity row = orderRepo.findById(orderId).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        UUID customerId = optionalUuid(p, "customerPartnerId");
        String customerName = resolvePartnerName(customerId);
        int lineCount = p.has("lines") && p.get("lines").isArray() ? p.get("lines").size() : 0;
        if (row == null) {
            row = new OrderSummaryEntity(
                    orderId,
                    text(p, "orderNo"),
                    uuid(p, "warehouseId"),
                    customerId,
                    customerName,
                    "RECEIVED",
                    optionalText(p, "source"),
                    optionalDate(p, "requiredShipDate"),
                    lineCount,
                    null,
                    occurredAt,
                    null,
                    occurredAt);
            orderRepo.save(row);
        } else {
            row.applyReceived(text(p, "orderNo"), uuid(p, "warehouseId"), customerId,
                    customerName, optionalText(p, "source"),
                    optionalDate(p, "requiredShipDate"), lineCount, occurredAt, occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onOrderCancelled(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        UUID orderId = uuid(p, "orderId");
        Instant occurredAt = envelope.occurredAt();
        OrderSummaryEntity row = orderRepo.findById(orderId).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        if (row == null) {
            // Out-of-order: cancelled before received. Insert a thin row;
            // received event (if it ever arrives) will not re-CREATE because
            // the LWW guard sees this row's last_event_at and skips.
            row = new OrderSummaryEntity(
                    orderId,
                    optionalText(p, "orderNo") == null ? "" : optionalText(p, "orderNo"),
                    new UUID(0, 0),
                    null, null, "CANCELLED", null, null, 0, null, null, null, occurredAt);
            orderRepo.save(row);
        } else {
            row.applyCancelled(occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onShippingConfirmed(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        UUID shipmentId = uuid(p, "shipmentId");
        UUID orderId = uuid(p, "orderId");
        UUID warehouseId = uuid(p, "warehouseId");
        Instant occurredAt = envelope.occurredAt();
        Instant shippedAt = optionalInstant(p, "shippedAt");
        Instant effectiveShipped = shippedAt == null ? occurredAt : shippedAt;

        // ShipmentSummary is append-style (one row per shipment). LWW guards
        // late re-deliveries.
        ShipmentSummaryEntity ship = shipmentRepo.findById(shipmentId).orElse(null);
        if (ship != null && ship.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        int totalQty = 0;
        if (p.has("lines") && p.get("lines").isArray()) {
            for (JsonNode line : p.get("lines")) {
                totalQty += optionalInteger(line, "qtyConfirmed", 0);
            }
        }
        if (ship == null) {
            ship = new ShipmentSummaryEntity(
                    shipmentId,
                    orderId,
                    optionalText(p, "orderNo"),
                    warehouseId,
                    optionalText(p, "shipmentNo"),
                    optionalText(p, "carrierCode"),
                    optionalText(p, "trackingNo"),
                    effectiveShipped,
                    totalQty,
                    occurredAt);
            shipmentRepo.save(ship);
        } else {
            ship.apply(orderId, optionalText(p, "orderNo"), warehouseId,
                    optionalText(p, "shipmentNo"), optionalText(p, "carrierCode"),
                    optionalText(p, "trackingNo"), effectiveShipped, totalQty, occurredAt);
        }

        // Cross-aggregate denorm: also stamp the order_summary row.
        orderRepo.findById(orderId).ifPresent(o -> {
            if (!o.getLastEventAt().isAfter(occurredAt)) {
                o.applyShipped(effectiveShipped, occurredAt);
            }
        });

        // Throughput counter — atomic LWW upsert. INSERT ... ON CONFLICT DO
        // UPDATE WHERE last_event_at < EXCLUDED.last_event_at handles both the
        // initial insert and the LWW guard at the SQL layer; 0 affected rows
        // simply means a newer event already won the race.
        LocalDate date = effectiveShipped.atZone(ZoneOffset.UTC).toLocalDate();
        throughputRepo.upsertIncrement(date, warehouseId, totalQty, occurredAt);
        return DedupeOutcome.APPLIED;
    }

    private String resolvePartnerName(UUID partnerId) {
        if (partnerId == null) return null;
        return partnerRepo.findById(partnerId).map(PartnerRefEntity::getName).orElse(null);
    }
}
