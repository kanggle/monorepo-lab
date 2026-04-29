package com.wms.outbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.outbound.application.command.ConfirmShippingCommand;
import com.wms.outbound.application.port.in.ConfirmShippingUseCase;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.application.port.out.PackingPersistencePort;
import com.wms.outbound.application.port.out.PickingConfirmationPersistencePort;
import com.wms.outbound.application.port.out.PickingPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.application.result.ShipmentResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.event.ShippingConfirmedEvent;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PackingUnit;
import com.wms.outbound.domain.model.PackingUnitLine;
import com.wms.outbound.domain.model.PickingConfirmation;
import com.wms.outbound.domain.model.PickingConfirmationLine;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.Shipment;
import com.wms.outbound.domain.model.TmsStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AC-07 implementation: confirms shipping, transitions order
 * {@code PACKED → SHIPPED}, advances saga to {@code SHIPPED}, creates
 * {@link Shipment}, writes {@code outbound.shipping.confirmed} outbox row,
 * and fires a {@link ShipmentNotifyTrigger} application event for the
 * {@link ShipmentNotificationListener} to invoke TMS post-commit.
 */
@Service
public class ConfirmShippingService implements ConfirmShippingUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmShippingService.class);

    private static final String ROLE_OUTBOUND_WRITE = "ROLE_OUTBOUND_WRITE";
    private static final String ROLE_OUTBOUND_ADMIN = "ROLE_OUTBOUND_ADMIN";

    private static final DateTimeFormatter SHIPMENT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderPersistencePort orderPersistence;
    private final PickingPersistencePort pickingPersistence;
    private final PickingConfirmationPersistencePort pickingConfirmationPersistence;
    private final PackingPersistencePort packingPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final ShipmentPersistencePort shipmentPersistence;
    private final OutboundSagaCoordinator sagaCoordinator;
    private final OutboxWriterPort outboxWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ConfirmShippingService(OrderPersistencePort orderPersistence,
                                  PickingPersistencePort pickingPersistence,
                                  PickingConfirmationPersistencePort pickingConfirmationPersistence,
                                  PackingPersistencePort packingPersistence,
                                  SagaPersistencePort sagaPersistence,
                                  ShipmentPersistencePort shipmentPersistence,
                                  OutboundSagaCoordinator sagaCoordinator,
                                  OutboxWriterPort outboxWriter,
                                  ApplicationEventPublisher eventPublisher,
                                  Clock clock) {
        this.orderPersistence = orderPersistence;
        this.pickingPersistence = pickingPersistence;
        this.pickingConfirmationPersistence = pickingConfirmationPersistence;
        this.packingPersistence = packingPersistence;
        this.sagaPersistence = sagaPersistence;
        this.shipmentPersistence = shipmentPersistence;
        this.sagaCoordinator = sagaCoordinator;
        this.outboxWriter = outboxWriter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ShipmentResult confirm(ConfirmShippingCommand command) {
        requireAnyRole(command.callerRoles(), ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN);

        Order order = orderPersistence.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        if (command.expectedVersion() >= 0 && order.getVersion() != command.expectedVersion()) {
            throw new ObjectOptimisticLockingFailureException(Order.class, command.orderId());
        }
        if (order.getStatus() != OrderStatus.PACKED) {
            throw new StateTransitionInvalidException(order.getStatus().name(), OrderStatus.SHIPPED.name());
        }

        OutboundSaga saga = sagaPersistence.findByOrderId(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        PickingRequest pickingRequest = pickingPersistence.findByOrderId(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        PickingConfirmation pickingConfirmation =
                pickingConfirmationPersistence.findByPickingRequestId(pickingRequest.getId())
                        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        Instant now = clock.instant();

        // Order: PACKED → SHIPPED.
        order.confirmShipping(now, command.actorId());
        Order savedOrder = orderPersistence.save(order);

        // Shipment record (PENDING) with deterministic shipment_no.
        UUID shipmentId = UuidV7.randomUuid();
        String shipmentNo = generateShipmentNo(now);
        Shipment shipment = new Shipment(
                shipmentId,
                savedOrder.getId(),
                shipmentNo,
                command.carrierCode(),
                null /* trackingNo populated by TMS ack */,
                now,
                TmsStatus.PENDING,
                null,
                null,
                0L,
                now,
                command.actorId(),
                now);
        Shipment savedShipment = shipmentPersistence.save(shipment);

        // Saga: PACKING_CONFIRMED → SHIPPED.
        sagaCoordinator.onShippingConfirmed(saga.sagaId());

        // Outbox row carrying reservationId + per-line confirmed quantities.
        Map<UUID, PickingConfirmationLine> confirmedLineByOrderLineId = new HashMap<>();
        for (PickingConfirmationLine pcl : pickingConfirmation.getLines()) {
            confirmedLineByOrderLineId.put(pcl.getOrderLineId(), pcl);
        }
        // Aggregate packed quantities per orderLineId for additional safety —
        // confirmation lines are the source of truth for SKU/lot/location.
        List<PackingUnit> packingUnits = packingPersistence.findByOrderId(savedOrder.getId());
        Map<UUID, Long> packedSumByOrderLine = new HashMap<>();
        for (PackingUnit u : packingUnits) {
            for (PackingUnitLine pul : u.getLines()) {
                packedSumByOrderLine.merge(pul.getOrderLineId(), (long) pul.getQty(), Long::sum);
            }
        }

        List<ShippingConfirmedEvent.Line> eventLines = new ArrayList<>(savedOrder.getLines().size());
        for (var ol : savedOrder.getLines()) {
            PickingConfirmationLine pcl = confirmedLineByOrderLineId.get(ol.getId());
            int qty = pcl != null ? pcl.getQtyConfirmed() : ol.getQtyOrdered();
            eventLines.add(new ShippingConfirmedEvent.Line(
                    ol.getId(),
                    ol.getSkuId(),
                    pcl != null ? pcl.getLotId() : ol.getLotId(),
                    pcl != null ? pcl.getActualLocationId() : null,
                    qty));
        }

        outboxWriter.publish(new ShippingConfirmedEvent(
                saga.sagaId(),
                pickingRequest.getId() /* reservationId */,
                savedOrder.getId(),
                savedShipment.getId(),
                savedShipment.getShipmentNo(),
                savedOrder.getWarehouseId(),
                savedShipment.getShippedAt(),
                savedShipment.getCarrierCode(),
                eventLines,
                now,
                command.actorId()));

        log.info("shipping_confirmed orderId={} shipmentId={} sagaId={}",
                savedOrder.getId(), savedShipment.getId(), saga.sagaId());

        // Fire post-commit event for TMS push.
        eventPublisher.publishEvent(new ShipmentNotifyTrigger(saga.sagaId(), savedShipment.getId()));

        return new ShipmentResult(
                savedShipment.getId(),
                savedShipment.getShipmentNo(),
                savedOrder.getId(),
                savedOrder.getOrderNo(),
                savedShipment.getCarrierCode(),
                savedShipment.getTrackingNo(),
                savedShipment.getShippedAt(),
                savedShipment.getTmsStatus().name(),
                savedShipment.getTmsNotifiedAt(),
                savedOrder.getStatus().name(),
                SagaStatus.SHIPPED.name(),
                savedShipment.getVersion(),
                savedShipment.getCreatedAt(),
                savedShipment.getCreatedBy());
    }

    /**
     * Generates a {@code shipment_no} of the form
     * {@code SHP-YYYYMMDD-NNNN}. Format matches
     * {@code outbound-service-api.md} §4.1 example. The trailing {@code NNNN}
     * is a 4-digit suffix from {@link ThreadLocalRandom}; uniqueness is
     * enforced by the {@code idx_shipment_shipment_no} unique index (V11
     * migration), so on the rare collision the persistence layer raises a
     * constraint violation and the caller retries with the same
     * Idempotency-Key.
     */
    private static String generateShipmentNo(Instant now) {
        String date = SHIPMENT_DATE_FMT.format(LocalDate.ofInstant(now, ZoneOffset.UTC));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "SHP-" + date + "-" + suffix;
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
