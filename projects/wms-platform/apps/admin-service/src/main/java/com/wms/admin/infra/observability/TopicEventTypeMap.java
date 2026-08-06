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
 * <p>Keys are the topics this service subscribes to — i.e. the topics the
 * producers actually publish. Two naming rules meet here, and both are
 * intentional:
 *
 * <ul>
 *   <li>{@code master-service} folds {@code master.<aggregate>.<action>} into
 *       one topic per aggregate: {@code wms.master.<aggregate>.v1}.</li>
 *   <li>{@code inbound-service} / {@code outbound-service} /
 *       {@code inventory-service} publish one topic per event type:
 *       {@code wms.<eventType>.v1}.</li>
 * </ul>
 *
 * <p>This map used to carry the rolled-up names {@code wms.inbound.asn.v1} and
 * {@code wms.outbound.order.v1}, which exist only as a documentation
 * convenience in {@code admin-events.md}. Nothing publishes to them, and the
 * consumers subscribed to them verbatim, so four event types reached nobody
 * ({@code TASK-BE-582}). {@code ProjectionTopicWiringTest} now enforces both
 * rules against this map and against the {@code @KafkaListener} wiring.
 */
public final class TopicEventTypeMap {

    private final Map<String, List<String>> topicToEventTypes;

    private TopicEventTypeMap(Map<String, List<String>> topicToEventTypes) {
        this.topicToEventTypes = Map.copyOf(topicToEventTypes);
    }

    public static TopicEventTypeMap defaults() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // master refs — one topic per aggregate (master-service folds actions)
        m.put("wms.master.warehouse.v1", List.of("master.warehouse.created", "master.warehouse.updated"));
        m.put("wms.master.zone.v1", List.of("master.zone.created", "master.zone.updated"));
        m.put("wms.master.location.v1", List.of("master.location.created", "master.location.updated"));
        m.put("wms.master.sku.v1", List.of("master.sku.created", "master.sku.updated"));
        m.put("wms.master.partner.v1", List.of("master.partner.created", "master.partner.updated"));
        m.put("wms.master.lot.v1", List.of("master.lot.created", "master.lot.updated"));
        // inbound — one topic per event type
        m.put("wms.inbound.asn.received.v1", List.of("inbound.asn.received"));
        m.put("wms.inbound.asn.cancelled.v1", List.of("inbound.asn.cancelled"));
        m.put("wms.inbound.asn.closed.v1", List.of("inbound.asn.closed"));
        m.put("wms.inbound.inspection.completed.v1", List.of("inbound.inspection.completed"));
        m.put("wms.inbound.putaway.instructed.v1", List.of("inbound.putaway.instructed"));
        m.put("wms.inbound.putaway.completed.v1", List.of("inbound.putaway.completed"));
        // outbound — one topic per event type
        m.put("wms.outbound.order.received.v1", List.of("outbound.order.received"));
        m.put("wms.outbound.order.cancelled.v1", List.of("outbound.order.cancelled"));
        m.put("wms.outbound.picking.requested.v1", List.of("outbound.picking.requested"));
        m.put("wms.outbound.picking.cancelled.v1", List.of("outbound.picking.cancelled"));
        m.put("wms.outbound.picking.completed.v1", List.of("outbound.picking.completed"));
        m.put("wms.outbound.packing.completed.v1", List.of("outbound.packing.completed"));
        m.put("wms.outbound.shipping.confirmed.v1", List.of("outbound.shipping.confirmed"));
        // inventory — one topic per event type
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
