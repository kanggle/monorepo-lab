package com.wms.outbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.outbound.application.command.ConfirmPickingCommand;
import com.wms.outbound.application.command.ConfirmPickingLineCommand;
import com.wms.outbound.application.port.in.ConfirmPickingUseCase;
import com.wms.outbound.application.port.out.MasterReadModelPort;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.application.port.out.PickingConfirmationPersistencePort;
import com.wms.outbound.application.port.out.PickingPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.result.PickingConfirmationLineResult;
import com.wms.outbound.application.result.PickingConfirmationResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.event.PickingCompletedEvent;
import com.wms.outbound.domain.exception.LotRequiredException;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.exception.PickingIncompleteException;
import com.wms.outbound.domain.exception.PickingRequestNotFoundException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PickingConfirmation;
import com.wms.outbound.domain.model.PickingConfirmationLine;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AC-05 implementation: confirms picking, transitions order PICKING → PICKED,
 * advances saga RESERVED → PICKING_CONFIRMED, writes
 * {@code outbound.picking.completed} outbox row in one TX.
 */
@Service
public class ConfirmPickingService implements ConfirmPickingUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmPickingService.class);

    private static final String ROLE_OUTBOUND_WRITE = "ROLE_OUTBOUND_WRITE";
    private static final String ROLE_OUTBOUND_ADMIN = "ROLE_OUTBOUND_ADMIN";

    private final OrderPersistencePort orderPersistence;
    private final PickingPersistencePort pickingPersistence;
    private final PickingConfirmationPersistencePort pickingConfirmationPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final OutboundSagaCoordinator sagaCoordinator;
    private final OutboxWriterPort outboxWriter;
    private final MasterReadModelPort masterReadModel;
    private final Clock clock;

    public ConfirmPickingService(OrderPersistencePort orderPersistence,
                                 PickingPersistencePort pickingPersistence,
                                 PickingConfirmationPersistencePort pickingConfirmationPersistence,
                                 SagaPersistencePort sagaPersistence,
                                 OutboundSagaCoordinator sagaCoordinator,
                                 OutboxWriterPort outboxWriter,
                                 MasterReadModelPort masterReadModel,
                                 Clock clock) {
        this.orderPersistence = orderPersistence;
        this.pickingPersistence = pickingPersistence;
        this.pickingConfirmationPersistence = pickingConfirmationPersistence;
        this.sagaPersistence = sagaPersistence;
        this.sagaCoordinator = sagaCoordinator;
        this.outboxWriter = outboxWriter;
        this.masterReadModel = masterReadModel;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PickingConfirmationResult confirm(ConfirmPickingCommand command) {
        requireAnyRole(command.callerRoles(), ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN);

        // 1) Load Order; must be in PICKING (Order.completePicking enforces).
        Order order = orderPersistence.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        // 2) Load PickingRequest by orderId; required.
        PickingRequest pickingRequest = pickingPersistence.findByOrderId(command.orderId())
                .orElseThrow(() -> new PickingRequestNotFoundException(command.orderId()));

        // 3) Load Saga; coordinator will assert RESERVED → PICKING_CONFIRMED.
        OutboundSaga saga = sagaPersistence.findByOrderId(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        Instant now = clock.instant();

        // 4) Validate every line vs OrderLine (qty match + LOT requirement).
        Map<UUID, OrderLine> orderLinesById = new HashMap<>();
        for (OrderLine ol : order.getLines()) {
            orderLinesById.put(ol.getId(), ol);
        }
        validateLines(command.lines(), orderLinesById);

        // 5) Build the PickingConfirmation aggregate (append-only).
        UUID pickingConfirmationId = UuidV7.randomUuid();
        List<PickingConfirmationLine> confirmationLines = new ArrayList<>(command.lines().size());
        for (ConfirmPickingLineCommand cl : command.lines()) {
            confirmationLines.add(new PickingConfirmationLine(
                    UuidV7.randomUuid(),
                    pickingConfirmationId,
                    cl.orderLineId(),
                    cl.skuId(),
                    cl.lotId(),
                    cl.actualLocationId(),
                    cl.qtyConfirmed()));
        }
        PickingConfirmation confirmation = new PickingConfirmation(
                pickingConfirmationId,
                pickingRequest.getId(),
                order.getId(),
                command.actorId(),
                now,
                command.notes(),
                confirmationLines);
        PickingConfirmation savedConfirmation = pickingConfirmationPersistence.save(confirmation);

        // 6) Order: PICKING → PICKED.
        order.completePicking(now, command.actorId());
        orderPersistence.save(order);

        // 7) Saga: RESERVED → PICKING_CONFIRMED via coordinator.
        sagaCoordinator.onPickingConfirmed(saga.sagaId());

        // 8) Outbox row (same TX).
        List<PickingCompletedEvent.Line> eventLines = savedConfirmation.getLines().stream()
                .map(l -> new PickingCompletedEvent.Line(
                        l.getOrderLineId(),
                        l.getSkuId(),
                        l.getLotId(),
                        l.getActualLocationId(),
                        l.getQtyConfirmed()))
                .toList();
        outboxWriter.publish(new PickingCompletedEvent(
                saga.sagaId(),
                order.getId(),
                savedConfirmation.getId(),
                savedConfirmation.getConfirmedBy(),
                savedConfirmation.getConfirmedAt(),
                eventLines,
                now,
                command.actorId()));

        log.info("picking_confirmed orderId={} pickingRequestId={} sagaId={}",
                order.getId(), pickingRequest.getId(), saga.sagaId());

        return new PickingConfirmationResult(
                savedConfirmation.getId(),
                pickingRequest.getId(),
                order.getId(),
                savedConfirmation.getConfirmedBy(),
                savedConfirmation.getConfirmedAt(),
                savedConfirmation.getNotes(),
                eventLines.stream()
                        .map(l -> new PickingConfirmationLineResult(
                                /* surrogate line id is not surfaced */ null,
                                l.orderLineId(), l.skuId(), l.lotId(),
                                l.actualLocationId(), l.qtyConfirmed()))
                        .toList(),
                order.getStatus().name(),
                SagaStatus.PICKING_CONFIRMED.name());
    }

    private void validateLines(List<ConfirmPickingLineCommand> lines,
                               Map<UUID, OrderLine> orderLinesById) {
        if (lines == null || lines.isEmpty()) {
            throw new PickingIncompleteException("no lines");
        }
        if (lines.size() != orderLinesById.size()) {
            throw new PickingIncompleteException(
                    "expected " + orderLinesById.size() + " lines, got " + lines.size());
        }
        for (ConfirmPickingLineCommand cl : lines) {
            OrderLine ol = orderLinesById.get(cl.orderLineId());
            if (ol == null) {
                throw new PickingIncompleteException(
                        "orderLineId not in order: " + cl.orderLineId());
            }
            if (cl.qtyConfirmed() != ol.getQtyOrdered()) {
                throw new PickingIncompleteException(
                        "qty mismatch on orderLineId=" + ol.getId()
                                + " expected=" + ol.getQtyOrdered()
                                + " actual=" + cl.qtyConfirmed());
            }
            // LOT requirement: SKU snapshot must declare tracking type.
            SkuSnapshot sku = masterReadModel.findSku(cl.skuId()).orElse(null);
            if (sku != null && sku.requiresLot() && cl.lotId() == null) {
                throw new LotRequiredException(cl.skuId());
            }
        }
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
