package com.wms.outbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.outbound.application.command.RecordReservedPickingCommand;
import com.wms.outbound.application.port.in.RecordReservedPickingUseCase;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.PickingPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.PickingRequestLine;
import com.wms.outbound.domain.model.PickingRequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialises the {@link PickingRequest} from a successful inventory
 * reservation — ADR-MONO-066 (ACCEPTED) option B.
 *
 * <p><b>What was broken.</b> The aggregate, its port, its adapter and its whole
 * downstream (confirm picking → pack → ship) all existed, but nothing in
 * production ever created a row: {@code PickingPersistencePort.save} had 0
 * production callers and 2 test callers. Those two tests seeded the row through
 * the port and verified the downstream on top of it, so the fact that "a picking
 * request exists" had never once been true in production was invisible to the
 * entire suite. Live consequence: every outbound order stopped at
 * {@code PICKING}, and {@code admin_shipment_summary} was structurally empty —
 * which in turn left half of ADR-MONO-065's tenant-isolation contract
 * unmeasurable (TASK-BE-584 AC-3).
 *
 * <p><b>Why here and not a picking planner.</b> The specs named a
 * {@code PickingPlanner} domain service in outbound as the owner of location
 * assignment; it was never implemented, and a code comment named inventory as
 * the owner "until PickingPlanner ships in BE-038" — a ticket that has since
 * closed. Two owners, so the question went to an ADR. The decision is B:
 * inventory already picks a concrete location while reserving (that is the
 * decision that actually locks stock) and already ships those ids back on
 * {@code wms.inventory.reserved.v1}. Outbound records them. Choosing again in
 * outbound would create a second, competing assignment and a new question about
 * which one wins.
 *
 * <p>Runs inside the consumer's transaction ({@code MANDATORY}) so the dedupe
 * row, the saga transition and this row commit or roll back together.
 */
@Service
public class RecordReservedPickingService implements RecordReservedPickingUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordReservedPickingService.class);

    private final PickingPersistencePort pickingPersistence;
    private final OrderPersistencePort orderPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final Clock clock;

    public RecordReservedPickingService(PickingPersistencePort pickingPersistence,
                                        OrderPersistencePort orderPersistence,
                                        SagaPersistencePort sagaPersistence,
                                        Clock clock) {
        this.pickingPersistence = pickingPersistence;
        this.orderPersistence = orderPersistence;
        this.sagaPersistence = sagaPersistence;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReservedPicking(RecordReservedPickingCommand command) {
        OutboundSaga saga = sagaPersistence.findById(command.sagaId()).orElse(null);
        if (saga == null) {
            log.warn("inventory.reserved for unknown sagaId={}; no picking request recorded",
                    command.sagaId());
            return;
        }

        // Idempotent by the aggregate's own invariant (one PickingRequest per
        // Order). The eventId dedupe upstream already covers redelivery of the
        // same event; this covers a re-emitted reservation with a fresh eventId.
        if (pickingPersistence.findByOrderId(saga.orderId()).isPresent()) {
            log.debug("picking request already exists for orderId={}; skipping", saga.orderId());
            return;
        }

        Order order = orderPersistence.findById(saga.orderId()).orElse(null);
        if (order == null) {
            log.warn("inventory.reserved for sagaId={} whose orderId={} is missing; "
                    + "no picking request recorded", command.sagaId(), saga.orderId());
            return;
        }

        // The reservation id inventory echoes back IS the picking request id
        // (domain-model.md §2 Invariants). Prefer the value on the wire; fall
        // back to the saga's own copy, which is what was sent in the first place.
        UUID pickingRequestId = command.pickingRequestId() != null
                ? command.pickingRequestId()
                : Objects.requireNonNullElse(saga.pickingRequestId(), saga.sagaId());

        Instant now = clock.instant();
        List<PickingRequestLine> lines = toPickingLines(command, order, pickingRequestId);

        PickingRequest saved = pickingPersistence.save(new PickingRequest(
                pickingRequestId,
                order.getId(),
                saga.sagaId(),
                command.warehouseId() != null ? command.warehouseId() : order.getWarehouseId(),
                // Submitted and accepted by inventory. RESERVE_FAILED is the
                // other terminal value and is written by the failure path.
                PickingRequestStatus.SUBMITTED,
                0L,
                now,
                now,
                lines));

        log.info("picking_request_recorded pickingRequestId={} orderId={} lines={}",
                saved.getId(), saved.getOrderId(), saved.getLines().size());
    }

    /**
     * Maps inventory's reserved lines onto the order's lines.
     *
     * <p>🔴 {@code inventory.reserved} carries no {@code orderLineId} — inventory
     * keys its lines by its own {@code reservationLineId} — so the join has to be
     * made here, and getting it wrong writes a picking instruction against the
     * wrong order line. Two facts make it exact rather than a guess:
     *
     * <ul>
     *   <li>inventory builds exactly one reservation line per requested line
     *       ({@code ReserveStockService} iterates {@code command.lines()}), so the
     *       two sides are 1:1 — never split, never merged;</li>
     *   <li>the request lines were built from {@code order.getLines()}, so
     *       {@code (skuId, lotId)} identifies the order line, and where an order
     *       repeats a {@code (skuId, lotId)} pair the reserved lines arrive in the
     *       same relative order.</li>
     * </ul>
     *
     * So: join on {@code (skuId, lotId)}, and resolve repeats positionally by
     * consuming each key's order lines in order. A count mismatch or an unmatched
     * key means the two services disagree about the order's shape, which is a
     * contract break — it throws rather than writing a plausible-looking row,
     * because the row would be silently wrong and everything downstream
     * (confirm → pack → ship) would build on it.
     */
    private static List<PickingRequestLine> toPickingLines(RecordReservedPickingCommand command,
                                                           Order order,
                                                           UUID pickingRequestId) {
        List<OrderLine> orderLines = order.getLines();
        if (command.lines() == null || command.lines().size() != orderLines.size()) {
            throw new IllegalStateException(
                    "inventory.reserved line count does not match the order: orderId="
                            + order.getId() + " orderLines=" + orderLines.size()
                            + " reservedLines="
                            + (command.lines() == null ? "null" : command.lines().size()));
        }

        Map<LineKey, Deque<OrderLine>> byKey = new HashMap<>();
        for (OrderLine ol : orderLines) {
            byKey.computeIfAbsent(new LineKey(ol.getSkuId(), ol.getLotId()),
                    k -> new ArrayDeque<>()).add(ol);
        }

        List<PickingRequestLine> lines = new ArrayList<>(command.lines().size());
        for (RecordReservedPickingCommand.Line rl : command.lines()) {
            Deque<OrderLine> candidates = byKey.get(new LineKey(rl.skuId(), rl.lotId()));
            if (candidates == null || candidates.isEmpty()) {
                throw new IllegalStateException(
                        "inventory.reserved line has no matching order line: orderId="
                                + order.getId() + " skuId=" + rl.skuId() + " lotId=" + rl.lotId());
            }
            OrderLine ol = candidates.poll();
            lines.add(new PickingRequestLine(
                    UuidV7.randomUuid(),
                    pickingRequestId,
                    ol.getId(),
                    rl.skuId(),
                    rl.lotId(),
                    rl.locationId(),
                    rl.quantity()));
        }
        return lines;
    }

    private record LineKey(UUID skuId, UUID lotId) {}
}
