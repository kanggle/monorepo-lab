package com.wms.inventory.application.service;

import com.wms.inventory.application.command.ConfirmReservationCommand;
import com.wms.inventory.application.port.in.ConfirmReservationUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.event.InventoryConfirmedEvent;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.exception.ReservationNotFoundException;
import com.wms.inventory.domain.exception.ReservationQuantityMismatchException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W5 confirm — terminal consume of reserved stock.
 *
 * <p>Pre-validates every shipped quantity matches the original reserved
 * quantity exactly (no partial shipments in v1) before any state mutates.
 * If any line mismatches → {@link ReservationQuantityMismatchException} and
 * the entire transaction rolls back.
 */
@Service
public class ConfirmReservationService implements ConfirmReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmReservationService.class);

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private final Counter confirmCounter;

    public ConfirmReservationService(ReservationRepository reservationRepository,
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
        this.confirmCounter = Counter.builder("inventory.mutation.count")
                .tag("operation", "CONFIRM")
                .description("Successful inventory mutations by operation")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public ReservationView confirm(ConfirmReservationCommand command) {
        Reservation reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + command.reservationId()));
        if (reservation.version() != command.expectedVersion()) {
            throw new ObjectOptimisticLockingFailureException(
                    Reservation.class, command.reservationId());
        }

        // Quantity validation pass — must match every line BEFORE any mutation.
        Map<java.util.UUID, ReservationLine> linesById = new HashMap<>();
        reservation.lines().forEach(l -> linesById.put(l.id(), l));
        for (ConfirmReservationCommand.Line line : command.lines()) {
            ReservationLine reserved = linesById.get(line.reservationLineId());
            if (reserved == null) {
                throw new ReservationNotFoundException(
                        "Reservation line not found: " + line.reservationLineId());
            }
            if (reserved.quantity() != line.shippedQuantity()) {
                throw new ReservationQuantityMismatchException(
                        line.reservationLineId(), reserved.quantity(), line.shippedQuantity());
            }
        }

        Instant now = clock.instant();
        List<InventoryConfirmedEvent.Line> eventLines = new ArrayList<>();
        for (ReservationLine line : reservation.lines()) {
            Inventory inv = inventoryRepository.findById(line.inventoryId())
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "Inventory row vanished mid-confirm: " + line.inventoryId()));
            InventoryMovement movement = inv.confirm(
                    line.quantity(), reservation.id(),
                    command.sourceEventId(), command.actorId(), now);
            inventoryRepository.updateWithVersionCheck(inv);
            movementRepository.save(movement);
            eventLines.add(new InventoryConfirmedEvent.Line(
                    line.id(), inv.id(), inv.locationId(), inv.skuId(), inv.lotId(),
                    line.quantity(), inv.reservedQty()));
        }

        reservation.confirm(now, command.actorId());
        Reservation persisted = reservationRepository.updateWithVersionCheck(reservation);

        outboxWriter.write(new InventoryConfirmedEvent(
                reservation.id(), reservation.pickingRequestId(), reservation.warehouseId(),
                eventLines, now, now, command.actorId()));
        confirmCounter.increment();
        log.info("inventory.confirmed emitted reservationId={}", reservation.id());
        return ReservationView.from(persisted);
    }
}
