package com.wms.inventory.adapter.in.messaging.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.domain.model.Inventory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.outbound.picking.requested.v1} and creates a
 * {@code Reservation} via {@link ReserveStockUseCase}.
 *
 * <p><strong>Cross-service contract (TASK-BE-431).</strong> The producer
 * (outbound-service {@code EventEnvelopeSerializer.pickingRequestedPayload})
 * knows only domain identity — it cannot know inventory-service's private
 * {@code inventory} row PK. So the wire carries
 * {@code (skuId, lotId?, locationId?, qtyToReserve)} per line and this consumer
 * <em>resolves and allocates</em> the concrete {@code inventoryId} rows:
 * <ul>
 *   <li>top-level {@code reservationId} is used as the {@code pickingRequestId}
 *       (outbound declares them 1:1 — {@code reservationId} = {@code PickingRequest.id}).</li>
 *   <li>line {@code locationId} present → the single row at
 *       {@code (locationId, skuId, lotId)} via {@link InventoryRepository#findByKey}.</li>
 *   <li>line {@code locationId} null (the v1 norm — location is bound later at
 *       picking) → candidate rows for {@code (warehouseId, skuId, lotId)} with
 *       {@code available_qty > 0}, allocating {@code qtyToReserve}
 *       greatest-available first (deterministic, spans rows only when one is
 *       insufficient).</li>
 * </ul>
 *
 * <p><strong>Shortfall / zero-stock.</strong> If the resolved rows cannot cover
 * a line's {@code qtyToReserve} (including the zero-rows case), the whole event
 * is a shortfall: no row is mutated and {@code inventory.reserve.failed} is
 * emitted (→ outbound auto-backorder). When at least one row resolves, the
 * service's {@code doReserve} shortfall pre-check emits the failure (the under-
 * covered qty is allocated to the best row so the pre-check trips); when
 * <em>no</em> row resolves at all, there is no {@code inventoryId} to reserve
 * against, so {@link ReserveStockUseCase#signalReserveFailed} emits the failure
 * directly from the natural-key identity. Either way: no NPE, no DLT loop, the
 * saga backorders.
 *
 * <p><strong>Layered idempotency.</strong> Two independent guards run in
 * series, both required by the architecture spec:
 * <ol>
 *   <li><strong>Outer (eventId dedupe, trait T8).</strong>
 *       {@link EventDedupePort#process(UUID, String, Runnable)} inserts a row
 *       into {@code inventory_event_dedupe} keyed by the envelope's
 *       {@code eventId}. A duplicate {@code eventId} (typical Kafka
 *       at-least-once redelivery) is short-circuited at the table's PK
 *       constraint and the use-case body is not re-executed.</li>
 *   <li><strong>Inner (pickingRequestId / aggregate-state guard).</strong>
 *       {@link com.wms.inventory.application.service.ReserveStockService}
 *       looks up an existing {@code Reservation} by
 *       {@code pickingRequestId} and short-circuits on cross-consumer races
 *       (e.g., a manual REST {@code POST /reservations} that arrived first
 *       with a different {@code eventId} but the same picking request).</li>
 * </ol>
 *
 * <p>The consumer is {@code @Transactional} so the dedupe row, the
 * reservation insert, the inventory updates, and the outbox row commit (or
 * roll back) atomically. {@code EventDedupePersistenceAdapter} declares
 * {@code Propagation.MANDATORY}, ensuring the dedupe write joins this TX
 * rather than creating its own.
 *
 * <p>Authoritative consumed shape: {@code inventory-events.md} §C2 (which
 * mirrors the producer SoT {@code outbound-events.md} §3).
 */
@Component
@Profile("!standalone")
public class PickingRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PickingRequestedConsumer.class);
    private static final String SYSTEM_ACTOR = "system:picking-requested-consumer";

    private final OutboundEventParser parser;
    private final EventDedupePort eventDedupePort;
    private final ReserveStockUseCase reserveStock;
    private final InventoryRepository inventoryRepository;

    public PickingRequestedConsumer(OutboundEventParser parser,
                                    EventDedupePort eventDedupePort,
                                    ReserveStockUseCase reserveStock,
                                    InventoryRepository inventoryRepository) {
        this.parser = parser;
        this.eventDedupePort = eventDedupePort;
        this.reserveStock = reserveStock;
        this.inventoryRepository = inventoryRepository;
    }

    @KafkaListener(
            topics = "${inventory.kafka.topics.outbound-picking-requested:wms.outbound.picking.requested.v1}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        OutboundEventParser.Parsed envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "picking-requested");
        try {
            Resolved resolved = resolve(envelope);
            log.debug("Processing outbound.picking.requested eventId={} pickingRequestId={} resolved={}",
                    envelope.eventId(), resolved.pickingRequestId(),
                    resolved.isReservable() ? "RESERVABLE" : "SHORTFALL");
            EventDedupePort.Outcome outcome = eventDedupePort.process(
                    envelope.eventId(), envelope.eventType(),
                    // TASK-MONO-196 / TASK-BE-431: the event path emits
                    // inventory.reserve.failed on a shortfall (→ outbound
                    // auto-backorder) instead of throwing/DLT — whether the
                    // shortfall was caught here (no row resolved) or by the
                    // service's pre-check (rows resolved but insufficient).
                    () -> apply(resolved));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("outbound.picking.requested eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void apply(Resolved resolved) {
        if (resolved.isReservable()) {
            reserveStock.reserveForPickingEvent(resolved.command());
        } else {
            reserveStock.signalReserveFailed(
                    resolved.pickingRequestId(), SYSTEM_ACTOR, resolved.shortfalls());
        }
    }

    /**
     * Read the wire, resolve each line's inventory row(s) and allocate.
     *
     * <p>Returns either a reservable {@link ReserveStockCommand} (every line
     * fully covered by available stock) or a list of natural-key shortfalls
     * (at least one line under-covered, including zero-rows). The two are
     * mutually exclusive: a single under-covered line fails the whole event so
     * no partial mutation occurs (AC-4).
     */
    private Resolved resolve(OutboundEventParser.Parsed envelope) {
        JsonNode payload = envelope.payload();
        UUID pickingRequestId = UUID.fromString(requireText(payload, "reservationId"));
        UUID warehouseId = UUID.fromString(requireText(payload, "warehouseId"));
        int ttlSeconds = payload.has("ttlSeconds") && !payload.get("ttlSeconds").isNull()
                ? payload.get("ttlSeconds").asInt() : 86_400;
        JsonNode lines = payload.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            throw new IllegalArgumentException("outbound.picking.requested has no lines");
        }

        List<ReserveStockCommand.Line> commandLines = new ArrayList<>(lines.size());
        List<ReserveStockUseCase.Shortfall> shortfalls = new ArrayList<>();

        for (JsonNode line : lines) {
            UUID skuId = UUID.fromString(requireText(line, "skuId"));
            UUID lotId = optionalUuid(line, "lotId");
            UUID locationId = optionalUuid(line, "locationId");
            int qtyToReserve = line.get("qtyToReserve").asInt();

            List<Inventory> candidates = resolveCandidates(warehouseId, skuId, lotId, locationId);
            int totalAvailable = candidates.stream().mapToInt(Inventory::availableQty).sum();

            if (totalAvailable < qtyToReserve) {
                // Shortfall (incl. zero rows). Record the natural-key identity;
                // inventoryId is the best-available row id when one exists, else
                // null (diagnostic only — outbound correlates by pickingRequestId).
                UUID hintRow = candidates.isEmpty() ? null : candidates.get(0).id();
                shortfalls.add(new ReserveStockUseCase.Shortfall(
                        hintRow, skuId, lotId, locationId, qtyToReserve, totalAvailable));
                continue;
            }
            // Coverable — allocate qtyToReserve greatest-available first.
            allocate(candidates, qtyToReserve, commandLines);
        }

        if (!shortfalls.isEmpty()) {
            return Resolved.shortfall(pickingRequestId, shortfalls);
        }
        ReserveStockCommand command = new ReserveStockCommand(
                pickingRequestId, warehouseId, commandLines, ttlSeconds,
                envelope.eventId(), SYSTEM_ACTOR, null);
        return Resolved.reservable(pickingRequestId, command);
    }

    private List<Inventory> resolveCandidates(UUID warehouseId, UUID skuId, UUID lotId, UUID locationId) {
        if (locationId != null) {
            // Specific row requested. Treat available_qty == 0 as no candidate
            // so the shortfall path is exercised consistently with the null-loc
            // resolution (which filters available_qty > 0).
            return inventoryRepository.findByKey(locationId, skuId, lotId)
                    .filter(inv -> inv.availableQty() > 0)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return inventoryRepository.findAvailableByWarehouseSkuLot(warehouseId, skuId, lotId);
    }

    /**
     * Allocate {@code qty} across {@code candidates} (already ordered greatest-
     * available first) into one {@link ReserveStockCommand.Line} per row drawn
     * from. Precondition: the candidates' total available {@code >= qty}.
     */
    private static void allocate(List<Inventory> candidates, int qty,
                                 List<ReserveStockCommand.Line> out) {
        int remaining = qty;
        for (Inventory inv : candidates) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, inv.availableQty());
            if (take <= 0) {
                continue;
            }
            out.add(new ReserveStockCommand.Line(inv.id(), take));
            remaining -= take;
        }
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : UUID.fromString(v.asText());
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            throw new IllegalArgumentException(
                    "outbound.picking.requested missing or non-text field: " + field);
        }
        return v.asText();
    }

    /**
     * Resolution outcome: exactly one of {@code command} (reservable) or
     * {@code shortfalls} (backorder) is populated.
     */
    private record Resolved(UUID pickingRequestId,
                            ReserveStockCommand command,
                            List<ReserveStockUseCase.Shortfall> shortfalls) {

        static Resolved reservable(UUID pickingRequestId, ReserveStockCommand command) {
            return new Resolved(pickingRequestId, command, List.of());
        }

        static Resolved shortfall(UUID pickingRequestId,
                                  List<ReserveStockUseCase.Shortfall> shortfalls) {
            return new Resolved(pickingRequestId, null, List.copyOf(shortfalls));
        }

        boolean isReservable() {
            return command != null;
        }
    }
}
