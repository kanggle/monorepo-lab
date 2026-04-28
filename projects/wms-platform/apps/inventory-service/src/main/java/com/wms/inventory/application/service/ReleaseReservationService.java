package com.wms.inventory.application.service;

import com.wms.inventory.application.command.ReleaseReservationCommand;
import com.wms.inventory.application.port.in.ReleaseReservationUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.event.InventoryReleasedEvent;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.exception.ReservationNotFoundException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.ReleasedReason;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Release a reservation. Used by:
 * <ul>
 *   <li>{@code PickingCancelledConsumer} (reason = {@code CANCELLED})</li>
 *   <li>REST {@code POST /reservations/{id}/release} (CANCELLED or MANUAL)</li>
 *   <li>{@code ReservationExpiryJob} (reason = {@code EXPIRED})</li>
 * </ul>
 *
 * <p>For each line: {@code Inventory.release(qty, reservationId, reason)}
 * moves quantity from RESERVED back to AVAILABLE; two Movement rows per line
 * (RESERVED -N + AVAILABLE +N). One outbox event covers all lines.
 */
@Service
public class ReleaseReservationService implements ReleaseReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseReservationService.class);

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private final Counter releaseCounter;

    public ReleaseReservationService(ReservationRepository reservationRepository,
                                     InventoryRepository inventoryRepository,
                                     InventoryMovementRepository movementRepository,
                                     OutboxWriter outboxWriter,
                                     Clock clock,
                                     MeterRegistry meterRegistry) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
        this.releaseCounter = Counter.builder("inventory.mutation.count")
                .tag("operation", "RELEASE")
                .description("Successful inventory mutations by operation")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public ReservationView release(ReleaseReservationCommand command) {
        Reservation reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + command.reservationId()));
        if (command.expectedVersion() != null
                && reservation.version() != command.expectedVersion()) {
            throw new ObjectOptimisticLockingFailureException(
                    Reservation.class, command.reservationId());
        }

        Instant now = clock.instant();
        ReasonCode reasonCode = reasonCodeFor(command.reason());
        List<InventoryReleasedEvent.Line> eventLines = new ArrayList<>();

        for (ReservationLine line : reservation.lines()) {
            Inventory inv = inventoryRepository.findById(line.inventoryId())
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "Inventory row vanished mid-release: " + line.inventoryId()));
            List<InventoryMovement> movements = inv.release(
                    line.quantity(), reservation.id(), reasonCode,
                    command.sourceEventId(), command.actorId(), now);
            inventoryRepository.updateWithVersionCheck(inv);
            movements.forEach(movementRepository::save);
            eventLines.add(new InventoryReleasedEvent.Line(
                    line.id(), inv.id(), inv.locationId(), inv.skuId(), inv.lotId(),
                    line.quantity(), inv.availableQty(), inv.reservedQty()));
        }

        reservation.release(command.reason(), now, command.actorId());
        Reservation persisted = reservationRepository.updateWithVersionCheck(reservation);

        outboxWriter.write(new InventoryReleasedEvent(
                reservation.id(), reservation.pickingRequestId(), reservation.warehouseId(),
                command.reason(), eventLines, now, now, command.actorId()));
        releaseCounter.increment();
        log.info("inventory.released emitted reservationId={} reason={}",
                reservation.id(), command.reason());
        return ReservationView.from(persisted);
    }

    /** Used by the TTL job to release without a caller-supplied version. */
    public ReservationView releaseExpired(UUID reservationId, String actorId) {
        return release(new ReleaseReservationCommand(
                reservationId, ReleasedReason.EXPIRED, null, null, actorId));
    }

    private static ReasonCode reasonCodeFor(ReleasedReason reason) {
        return switch (reason) {
            case CANCELLED -> ReasonCode.PICKING_CANCELLED;
            case EXPIRED -> ReasonCode.PICKING_EXPIRED;
            case MANUAL -> ReasonCode.PICKING_CANCELLED;
        };
    }
}
