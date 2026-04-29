package com.wms.outbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.outbound.application.command.ConfirmPackingCommand;
import com.wms.outbound.application.command.CreatePackingUnitCommand;
import com.wms.outbound.application.command.CreatePackingUnitLineCommand;
import com.wms.outbound.application.command.SealPackingUnitCommand;
import com.wms.outbound.application.port.in.ConfirmPackingUseCase;
import com.wms.outbound.application.port.in.CreatePackingUnitUseCase;
import com.wms.outbound.application.port.in.SealPackingUnitUseCase;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.application.port.out.PackingPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.PackingUnitLineResult;
import com.wms.outbound.application.result.PackingUnitResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.event.PackingCompletedEvent;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.exception.PackingIncompleteException;
import com.wms.outbound.domain.exception.PackingUnitNotFoundException;
import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PackingType;
import com.wms.outbound.domain.model.PackingUnit;
import com.wms.outbound.domain.model.PackingUnitLine;
import com.wms.outbound.domain.model.PackingUnitStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AC-06 implementation: PackingUnit lifecycle (create / seal) and
 * order-level packing confirmation.
 *
 * <p>Implements the implicit start-packing pattern: the first call to
 * {@link #create} for an Order in {@code PICKED} transitions the Order to
 * {@code PACKING}. {@link #confirm} validates completeness and advances
 * Order {@code PACKING → PACKED} + Saga
 * {@code PICKING_CONFIRMED → PACKING_CONFIRMED}, writing
 * {@code outbound.packing.completed} to the outbox in one TX.
 */
@Service
public class PackingService implements CreatePackingUnitUseCase,
        SealPackingUnitUseCase, ConfirmPackingUseCase {

    private static final Logger log = LoggerFactory.getLogger(PackingService.class);

    private static final String ROLE_OUTBOUND_WRITE = "ROLE_OUTBOUND_WRITE";
    private static final String ROLE_OUTBOUND_ADMIN = "ROLE_OUTBOUND_ADMIN";

    private final OrderPersistencePort orderPersistence;
    private final PackingPersistencePort packingPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final OutboundSagaCoordinator sagaCoordinator;
    private final OutboxWriterPort outboxWriter;
    private final Clock clock;

    public PackingService(OrderPersistencePort orderPersistence,
                          PackingPersistencePort packingPersistence,
                          SagaPersistencePort sagaPersistence,
                          OutboundSagaCoordinator sagaCoordinator,
                          OutboxWriterPort outboxWriter,
                          Clock clock) {
        this.orderPersistence = orderPersistence;
        this.packingPersistence = packingPersistence;
        this.sagaPersistence = sagaPersistence;
        this.sagaCoordinator = sagaCoordinator;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    //  CreatePackingUnitUseCase
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public PackingUnitResult create(CreatePackingUnitCommand command) {
        requireAnyRole(command.callerRoles(), ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN);

        Order order = orderPersistence.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        // Implicit startPacking on first unit creation when Order is PICKED.
        if (order.getStatus() == OrderStatus.PICKED) {
            Instant now = clock.instant();
            order.startPacking(now, command.actorId());
            orderPersistence.save(order);
        } else if (order.getStatus() != OrderStatus.PACKING) {
            throw new StateTransitionInvalidException(order.getStatus().name(), OrderStatus.PACKING.name());
        }

        Instant now = clock.instant();
        UUID unitId = UuidV7.randomUuid();
        List<PackingUnitLine> lines = new ArrayList<>(command.lines().size());
        for (CreatePackingUnitLineCommand cl : command.lines()) {
            lines.add(new PackingUnitLine(
                    UuidV7.randomUuid(),
                    unitId,
                    cl.orderLineId(),
                    cl.skuId(),
                    cl.lotId(),
                    cl.qty()));
        }
        PackingUnit unit = new PackingUnit(
                unitId,
                order.getId(),
                command.cartonNo(),
                PackingType.valueOf(command.packingType()),
                command.weightGrams(),
                command.lengthMm(),
                command.widthMm(),
                command.heightMm(),
                command.notes(),
                PackingUnitStatus.OPEN,
                0L,
                now,
                now,
                lines);
        PackingUnit saved = packingPersistence.save(unit);

        log.info("packing_unit_created orderId={} unitId={} cartonNo={}",
                order.getId(), saved.getId(), saved.getCartonNo());
        return toResult(saved, order.getStatus().name());
    }

    // ------------------------------------------------------------------
    //  SealPackingUnitUseCase
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public PackingUnitResult seal(SealPackingUnitCommand command) {
        requireAnyRole(command.callerRoles(), ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN);

        PackingUnit unit = packingPersistence.findById(command.packingUnitId())
                .orElseThrow(() -> new PackingUnitNotFoundException(command.packingUnitId()));
        if (!unit.getOrderId().equals(command.orderId())) {
            throw new PackingUnitNotFoundException(command.packingUnitId());
        }
        if (command.expectedVersion() >= 0 && unit.getVersion() != command.expectedVersion()) {
            throw new ObjectOptimisticLockingFailureException(PackingUnit.class, command.packingUnitId());
        }

        Order order = orderPersistence.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        Instant now = clock.instant();
        unit.seal(now);
        PackingUnit saved = packingPersistence.save(unit);

        log.info("packing_unit_sealed orderId={} unitId={}", order.getId(), saved.getId());
        return toResult(saved, order.getStatus().name());
    }

    // ------------------------------------------------------------------
    //  ConfirmPackingUseCase
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OrderResult confirm(ConfirmPackingCommand command) {
        requireAnyRole(command.callerRoles(), ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN);

        Order order = orderPersistence.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        if (command.expectedVersion() >= 0 && order.getVersion() != command.expectedVersion()) {
            throw new ObjectOptimisticLockingFailureException(Order.class, command.orderId());
        }

        OutboundSaga saga = sagaPersistence.findByOrderId(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        List<PackingUnit> units = packingPersistence.findByOrderId(command.orderId());
        if (units.isEmpty()) {
            throw new PackingIncompleteException("no packing units for order " + command.orderId());
        }
        // All must be SEALED.
        for (PackingUnit u : units) {
            if (!u.isSealed()) {
                throw new PackingIncompleteException(
                        "unit not sealed: cartonNo=" + u.getCartonNo());
            }
        }
        // Sum of PackingUnitLine.qty per orderLineId must equal qtyOrdered.
        Map<UUID, Long> sumByOrderLine = new HashMap<>();
        for (PackingUnit u : units) {
            for (PackingUnitLine l : u.getLines()) {
                sumByOrderLine.merge(l.getOrderLineId(), (long) l.getQty(), Long::sum);
            }
        }
        for (OrderLine ol : order.getLines()) {
            long packed = sumByOrderLine.getOrDefault(ol.getId(), 0L);
            if (packed != ol.getQtyOrdered()) {
                throw new PackingIncompleteException(
                        "orderLineId=" + ol.getId()
                                + " expected=" + ol.getQtyOrdered()
                                + " packed=" + packed);
            }
        }

        Instant now = clock.instant();
        order.completePacking(now, command.actorId());
        Order savedOrder = orderPersistence.save(order);

        sagaCoordinator.onPackingConfirmed(saga.sagaId());

        // Outbox row.
        List<PackingCompletedEvent.Unit> unitPayloads = new ArrayList<>(units.size());
        long totalCartons = 0L;
        Integer totalWeight = null;
        for (PackingUnit u : units) {
            totalCartons++;
            if (u.getWeightGrams() != null) {
                totalWeight = (totalWeight == null ? 0 : totalWeight) + u.getWeightGrams();
            }
            List<PackingCompletedEvent.Line> lineP = u.getLines().stream()
                    .map(l -> new PackingCompletedEvent.Line(
                            l.getOrderLineId(), l.getSkuId(), l.getLotId(), l.getQty()))
                    .toList();
            unitPayloads.add(new PackingCompletedEvent.Unit(
                    u.getId(), u.getCartonNo(), u.getPackingType().name(),
                    u.getWeightGrams(), u.getLengthMm(), u.getWidthMm(), u.getHeightMm(),
                    lineP));
        }
        outboxWriter.publish(new PackingCompletedEvent(
                savedOrder.getId(),
                savedOrder.getOrderNo(),
                savedOrder.getWarehouseId(),
                now,
                unitPayloads,
                (int) totalCartons,
                totalWeight,
                now,
                command.actorId()));

        log.info("packing_confirmed orderId={} units={}", savedOrder.getId(), units.size());

        OutboundSaga refreshed = sagaPersistence.findById(saga.sagaId()).orElse(saga);
        return OrderResultMapper.toResult(savedOrder, refreshed);
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private static PackingUnitResult toResult(PackingUnit u, String orderStatus) {
        List<PackingUnitLineResult> lines = u.getLines().stream()
                .map(l -> new PackingUnitLineResult(
                        l.getId(), l.getOrderLineId(), l.getSkuId(), l.getLotId(), l.getQty()))
                .toList();
        return new PackingUnitResult(
                u.getId(),
                u.getOrderId(),
                u.getCartonNo(),
                u.getPackingType().name(),
                u.getWeightGrams(),
                u.getLengthMm(),
                u.getWidthMm(),
                u.getHeightMm(),
                u.getNotes(),
                u.getStatus().name(),
                lines,
                orderStatus,
                u.getVersion(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }

    private static void requireAnyRole(Set<String> roles, String... required) {
        if (roles == null) {
            throw new AccessDeniedException("Role required: any of " + java.util.Arrays.toString(required));
        }
        for (String r : required) {
            if (roles.contains(r)) {
                return;
            }
        }
        throw new AccessDeniedException("Role required: any of " + java.util.Arrays.toString(required));
    }
}
