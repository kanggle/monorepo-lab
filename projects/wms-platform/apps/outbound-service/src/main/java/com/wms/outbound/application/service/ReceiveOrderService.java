package com.wms.outbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.outbound.application.command.ReceiveOrderCommand;
import com.wms.outbound.application.command.ReceiveOrderLineCommand;
import com.wms.outbound.application.port.in.ReceiveOrderUseCase;
import com.wms.outbound.application.port.out.MasterReadModelPort;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.domain.event.OrderReceivedEvent;
import com.wms.outbound.domain.event.PickingRequestedEvent;
import com.wms.outbound.domain.exception.OrderNoDuplicateException;
import com.wms.outbound.domain.exception.PartnerInvalidTypeException;
import com.wms.outbound.domain.exception.SkuInactiveException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AC-04 implementation: creates {@link Order} (RECEIVED→PICKING),
 * {@link OutboundSaga} (REQUESTED), and writes both
 * {@code outbound.order.received} and {@code outbound.picking.requested}
 * outbox rows in a single TX.
 */
@Service
public class ReceiveOrderService implements ReceiveOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReceiveOrderService.class);

    private static final String ROLE_OUTBOUND_WRITE = "ROLE_OUTBOUND_WRITE";
    private static final String SYSTEM_ACTOR_PREFIX = "system:";

    private final OrderPersistencePort orderPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final OutboxWriterPort outboxWriter;
    private final MasterReadModelPort masterReadModel;
    private final Clock clock;

    public ReceiveOrderService(OrderPersistencePort orderPersistence,
                               SagaPersistencePort sagaPersistence,
                               OutboxWriterPort outboxWriter,
                               MasterReadModelPort masterReadModel,
                               Clock clock) {
        this.orderPersistence = orderPersistence;
        this.sagaPersistence = sagaPersistence;
        this.outboxWriter = outboxWriter;
        this.masterReadModel = masterReadModel;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrderResult receive(ReceiveOrderCommand command) {
        if (!isSystemActor(command.actorId())) {
            AuthorizationGuards.requireRole(command.callerRoles(), ROLE_OUTBOUND_WRITE);
        }

        // 1) Validate the customer partner.
        PartnerSnapshot partner = validateCustomerPartner(command);

        // 2) Reject duplicate orderNo early (DB unique constraint is the
        //    final guard — this avoids burning UUIDs on guaranteed conflicts).
        if (orderPersistence.existsByOrderNo(command.orderNo())) {
            throw new OrderNoDuplicateException(command.orderNo());
        }

        Instant now = clock.instant();
        UUID orderId = UuidV7.randomUuid();

        // 3) Build OrderLines, validating SKUs are ACTIVE per local read model.
        List<OrderReceivedEvent.Line> eventLines = new ArrayList<>(command.lines().size());
        List<OrderLine> lines = buildOrderLines(command, orderId, eventLines);

        // 4) Build the Order aggregate — status starts at RECEIVED, then
        //    immediately advances to PICKING in the same TX (saga starts).
        Order order = buildOrderAggregate(command, orderId, lines, now);
        Order saved = orderPersistence.save(order);

        // 5) Create the saga in REQUESTED state. pickingRequestId == sagaId
        //    until TASK-BE-038 introduces the PickingRequest aggregate.
        UUID sagaId = UuidV7.randomUuid();
        OutboundSaga saga = OutboundSaga.newRequested(sagaId, saved.getId(), now);
        OutboundSaga savedSaga = sagaPersistence.save(saga);

        // 6) Write the two outbox rows.
        emitOrderReceivedAndPickingRequested(saved, savedSaga, partner, eventLines, command, now);

        log.info("order_received orderId={} orderNo={} sagaId={} status={}",
                saved.getId(), saved.getOrderNo(), savedSaga.sagaId(), saved.getStatus());
        return OrderResultMapper.toResult(saved, savedSaga);
    }

    // ------------------------------------------------------------------
    //  Private helpers
    // ------------------------------------------------------------------

    /**
     * Validates that the customer partner exists in the read model and is
     * eligible to receive orders (active status + correct type).
     *
     * @throws PartnerInvalidTypeException if the partner is not found, not
     *         active, or not of a receiving type
     */
    private PartnerSnapshot validateCustomerPartner(ReceiveOrderCommand command) {
        PartnerSnapshot partner = masterReadModel.findPartner(command.customerPartnerId())
                .orElseThrow(() -> new PartnerInvalidTypeException(
                        command.customerPartnerId(), "not found in read model"));
        if (!partner.canReceive()) {
            throw new PartnerInvalidTypeException(command.customerPartnerId(),
                    "status=" + partner.status() + " type=" + partner.partnerType());
        }
        return partner;
    }

    /**
     * Builds the {@link OrderLine} list from the command, validating each SKU
     * against the local read model. Simultaneously populates
     * {@code eventLinesAccumulator} with the corresponding
     * {@link OrderReceivedEvent.Line} entries for the outbox row.
     *
     * @param command              the receive command
     * @param orderId              the newly-assigned order UUID
     * @param eventLinesAccumulator mutable list that receives event line entries
     *                              (populated as a side-effect)
     * @return the built {@link OrderLine} list
     * @throws SkuInactiveException if any SKU is not found or inactive
     */
    private List<OrderLine> buildOrderLines(ReceiveOrderCommand command,
                                            UUID orderId,
                                            List<OrderReceivedEvent.Line> eventLinesAccumulator) {
        List<OrderLine> lines = new ArrayList<>(command.lines().size());
        for (ReceiveOrderLineCommand cl : command.lines()) {
            SkuSnapshot sku = masterReadModel.findSku(cl.skuId())
                    .orElseThrow(() -> new SkuInactiveException(cl.skuId()));
            if (!sku.isActive()) {
                throw new SkuInactiveException(cl.skuId());
            }
            UUID lineId = UuidV7.randomUuid();
            lines.add(new OrderLine(lineId, orderId, cl.lineNo(),
                    cl.skuId(), cl.lotId(), cl.qtyOrdered()));
            eventLinesAccumulator.add(new OrderReceivedEvent.Line(
                    lineId, cl.lineNo(), cl.skuId(), sku.skuCode(),
                    cl.lotId(), cl.qtyOrdered()));
        }
        return lines;
    }

    /**
     * Constructs the {@link Order} aggregate in {@code RECEIVED} status, then
     * immediately transitions it to {@code PICKING} via {@link Order#startPicking}
     * within the same TX. Does NOT persist.
     */
    private static Order buildOrderAggregate(ReceiveOrderCommand command,
                                             UUID orderId,
                                             List<OrderLine> lines,
                                             Instant now) {
        Order order = new Order(
                orderId,
                command.orderNo(),
                OrderSource.valueOf(command.source()),
                command.customerPartnerId(),
                command.warehouseId(),
                command.requiredShipDate(),
                command.notes(),
                command.shipTo(),
                command.tenantId(),
                OrderStatus.RECEIVED,
                0L,
                now, command.actorId(),
                now, command.actorId(),
                lines);
        order.startPicking(now, command.actorId());
        return order;
    }

    /**
     * Writes both {@code outbound.order.received} and
     * {@code outbound.picking.requested} outbox rows in the current TX.
     */
    private void emitOrderReceivedAndPickingRequested(Order saved,
                                                      OutboundSaga savedSaga,
                                                      PartnerSnapshot partner,
                                                      List<OrderReceivedEvent.Line> eventLines,
                                                      ReceiveOrderCommand command,
                                                      Instant now) {
        outboxWriter.publish(new OrderReceivedEvent(
                saved.getId(),
                saved.getOrderNo(),
                saved.getSource().name(),
                saved.getCustomerPartnerId(),
                partner.partnerCode(),
                saved.getWarehouseId(),
                saved.getRequiredShipDate(),
                saved.getShipTo(),
                eventLines,
                now,
                command.actorId()));

        List<PickingRequestedEvent.Line> pickingLines = saved.getLines().stream()
                .map(l -> new PickingRequestedEvent.Line(
                        l.getId(), l.getSkuId(), l.getLotId(),
                        null /* locationId — assigned by inventory until PickingPlanner ships in BE-038 */,
                        l.getQtyOrdered()))
                .toList();
        outboxWriter.publish(new PickingRequestedEvent(
                savedSaga.sagaId(),
                savedSaga.pickingRequestId(),
                saved.getId(),
                saved.getWarehouseId(),
                pickingLines,
                now,
                command.actorId()));
    }

    private static boolean isSystemActor(String actorId) {
        return actorId != null && actorId.startsWith(SYSTEM_ACTOR_PREFIX);
    }
}
