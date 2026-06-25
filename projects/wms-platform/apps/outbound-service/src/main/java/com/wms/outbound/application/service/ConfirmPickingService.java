package com.wms.outbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.outbound.application.command.ConfirmPickingCommand;
import com.wms.outbound.application.command.ConfirmPickingLineCommand;
import com.wms.outbound.application.port.in.ConfirmPickingUseCase;
import com.wms.outbound.application.port.out.CallerScopeProvider;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final CallerScopeProvider callerScopeProvider;
    private final Clock clock;

    public ConfirmPickingService(OrderPersistencePort orderPersistence,
                                 PickingPersistencePort pickingPersistence,
                                 PickingConfirmationPersistencePort pickingConfirmationPersistence,
                                 SagaPersistencePort sagaPersistence,
                                 OutboundSagaCoordinator sagaCoordinator,
                                 OutboxWriterPort outboxWriter,
                                 MasterReadModelPort masterReadModel,
                                 CallerScopeProvider callerScopeProvider,
                                 Clock clock) {
        this.orderPersistence = orderPersistence;
        this.pickingPersistence = pickingPersistence;
        this.pickingConfirmationPersistence = pickingConfirmationPersistence;
        this.sagaPersistence = sagaPersistence;
        this.sagaCoordinator = sagaCoordinator;
        this.outboxWriter = outboxWriter;
        this.masterReadModel = masterReadModel;
        this.callerScopeProvider = callerScopeProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PickingConfirmationResult confirm(ConfirmPickingCommand command) {
        AuthorizationGuards.requireAnyRole(command.callerRoles(), ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN);

        PickingAggregates agg = loadPickingAggregates(command.orderId());
        // Cross-tenant guard (TASK-MONO-304).
        callerScopeProvider.current().requireOrderAccess(
                agg.order().getTenantId(), agg.order().getId());

        Instant now = clock.instant();

        // Validate every line vs OrderLine (qty match + LOT requirement).
        Map<UUID, OrderLine> orderLinesById = new HashMap<>();
        for (OrderLine ol : agg.order().getLines()) {
            orderLinesById.put(ol.getId(), ol);
        }
        validateLines(command.lines(), orderLinesById);

        // Build the PickingConfirmation aggregate (append-only).
        PickingConfirmation confirmation = buildConfirmation(
                command, agg.pickingRequest().getId(), orderLinesById, now);
        PickingConfirmation savedConfirmation = pickingConfirmationPersistence.save(confirmation);

        // Order: PICKING → PICKED.
        agg.order().completePicking(now, command.actorId());
        orderPersistence.save(agg.order());

        // Saga: RESERVED → PICKING_CONFIRMED via coordinator.
        sagaCoordinator.onPickingConfirmed(agg.saga().sagaId());

        // Outbox row (same TX).
        List<PickingCompletedEvent.Line> eventLines = emitPickingCompletedOutbox(
                savedConfirmation, agg.order(), agg.saga().sagaId(), now, command.actorId());

        log.info("picking_confirmed orderId={} pickingRequestId={} sagaId={}",
                agg.order().getId(), agg.pickingRequest().getId(), agg.saga().sagaId());

        return new PickingConfirmationResult(
                savedConfirmation.getId(),
                agg.pickingRequest().getId(),
                agg.order().getId(),
                savedConfirmation.getConfirmedBy(),
                savedConfirmation.getConfirmedAt(),
                savedConfirmation.getNotes(),
                eventLines.stream()
                        .map(l -> new PickingConfirmationLineResult(
                                /* surrogate line id is not surfaced */ null,
                                l.orderLineId(), l.skuId(), l.lotId(),
                                l.actualLocationId(), l.qtyConfirmed()))
                        .toList(),
                agg.order().getStatus().name(),
                SagaStatus.PICKING_CONFIRMED.name());
    }

    // ------------------------------------------------------------------
    //  Private helpers
    // ------------------------------------------------------------------

    /** Carries the three aggregates loaded at the start of the confirm flow. */
    private record PickingAggregates(
            Order order,
            PickingRequest pickingRequest,
            OutboundSaga saga) {}

    /**
     * Loads {@link Order}, {@link PickingRequest}, and {@link OutboundSaga}
     * by {@code orderId}.
     *
     * @throws OrderNotFoundException           if the order or saga is missing
     * @throws PickingRequestNotFoundException  if the picking request is missing
     */
    private PickingAggregates loadPickingAggregates(UUID orderId) {
        // 1) Load Order; must be in PICKING (Order.completePicking enforces).
        Order order = orderPersistence.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        // 2) Load PickingRequest by orderId; required.
        PickingRequest pickingRequest = pickingPersistence.findByOrderId(orderId)
                .orElseThrow(() -> new PickingRequestNotFoundException(orderId));
        // 3) Load Saga; coordinator will assert RESERVED → PICKING_CONFIRMED.
        OutboundSaga saga = sagaPersistence.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return new PickingAggregates(order, pickingRequest, saga);
    }

    /**
     * Constructs the {@link PickingConfirmation} aggregate (append-only) from
     * the command lines. Does NOT persist.
     */
    private static PickingConfirmation buildConfirmation(ConfirmPickingCommand command,
                                                         UUID pickingRequestId,
                                                         Map<UUID, OrderLine> orderLinesById,
                                                         Instant now) {
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
        return new PickingConfirmation(
                pickingConfirmationId,
                pickingRequestId,
                command.orderId(),
                command.actorId(),
                now,
                command.notes(),
                confirmationLines);
    }

    /**
     * Builds the {@link PickingCompletedEvent.Line} list from the saved
     * confirmation and writes the {@code outbound.picking.completed} outbox
     * row within the current transaction boundary.
     *
     * @return the built event lines (reused for the result DTO)
     */
    private List<PickingCompletedEvent.Line> emitPickingCompletedOutbox(
            PickingConfirmation savedConfirmation,
            Order order,
            UUID sagaId,
            Instant now,
            String actorId) {
        List<PickingCompletedEvent.Line> eventLines = savedConfirmation.getLines().stream()
                .map(l -> new PickingCompletedEvent.Line(
                        l.getOrderLineId(),
                        l.getSkuId(),
                        l.getLotId(),
                        l.getActualLocationId(),
                        l.getQtyConfirmed()))
                .toList();
        outboxWriter.publish(new PickingCompletedEvent(
                sagaId,
                order.getId(),
                savedConfirmation.getId(),
                savedConfirmation.getConfirmedBy(),
                savedConfirmation.getConfirmedAt(),
                eventLines,
                now,
                actorId));
        return eventLines;
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

}
