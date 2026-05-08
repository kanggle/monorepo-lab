package com.wms.admin.infra.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static topic ↔ {@code eventType} map mirroring the producer / consumer
 * wiring in {@code admin-events.md § Consumed Events}.
 *
 * <p>Used by {@link KafkaLagProbe} to derive each topic's
 * {@code lastProjectedAt} from the {@code admin_event_dedupe.processed_at}
 * MAX over its eventTypes.
 *
 * <p>Each consumer-side aggregate topic ({@code wms.inbound.asn.v1},
 * {@code wms.outbound.order.v1}) folds the per-action split topics that the
 * producer service publishes (see the cross-reference note in
 * {@code admin-events.md} for the rationale).
 */
public final class TopicEventTypeMap {

    private final Map<String, List<String>> topicToEventTypes;

    private TopicEventTypeMap(Map<String, List<String>> topicToEventTypes) {
        this.topicToEventTypes = Map.copyOf(topicToEventTypes);
    }

    public static TopicEventTypeMap defaults() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // master refs
        m.put("wms.master.warehouse.v1", List.of("master.warehouse.created", "master.warehouse.updated"));
        m.put("wms.master.zone.v1", List.of("master.zone.created", "master.zone.updated"));
        m.put("wms.master.location.v1", List.of("master.location.created", "master.location.updated"));
        m.put("wms.master.sku.v1", List.of("master.sku.created", "master.sku.updated"));
        m.put("wms.master.partner.v1", List.of("master.partner.created", "master.partner.updated"));
        m.put("wms.master.lot.v1", List.of("master.lot.created", "master.lot.updated"));
        // inbound
        m.put("wms.inbound.asn.v1",
                List.of("inbound.asn.received", "inbound.asn.cancelled", "inbound.asn.closed"));
        m.put("wms.inbound.inspection.completed.v1", List.of("inbound.inspection.completed"));
        m.put("wms.inbound.putaway.completed.v1",
                List.of("inbound.putaway.instructed", "inbound.putaway.completed"));
        // outbound
        m.put("wms.outbound.order.v1",
                List.of("outbound.order.received", "outbound.order.cancelled"));
        m.put("wms.outbound.shipping.confirmed.v1",
                List.of("outbound.shipping.confirmed", "outbound.picking.requested",
                        "outbound.picking.cancelled", "outbound.picking.completed",
                        "outbound.packing.completed"));
        // inventory
        m.put("wms.inventory.received.v1", List.of("inventory.received"));
        m.put("wms.inventory.adjusted.v1", List.of("inventory.adjusted"));
        m.put("wms.inventory.transferred.v1", List.of("inventory.transferred"));
        m.put("wms.inventory.reserved.v1", List.of("inventory.reserved"));
        m.put("wms.inventory.released.v1", List.of("inventory.released"));
        m.put("wms.inventory.confirmed.v1", List.of("inventory.confirmed"));
        m.put("wms.inventory.alert.v1", List.of("inventory.low-stock-detected"));
        return new TopicEventTypeMap(m);
    }

    public List<String> topics() {
        return List.copyOf(topicToEventTypes.keySet());
    }

    public List<String> eventTypesFor(String topic) {
        return topicToEventTypes.getOrDefault(topic, List.of());
    }
}
