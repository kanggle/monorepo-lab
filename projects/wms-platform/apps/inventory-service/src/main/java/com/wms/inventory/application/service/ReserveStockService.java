package com.wms.inventory.application.service;

import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.event.InventoryReservedEvent;
import com.wms.inventory.domain.exception.DuplicateRequestException;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application service for the W4 reserve flow.
 *
 * <p>For each line: load Inventory by id (in deterministic id order to avoid
 * deadlocks under concurrent reciprocal reserves), apply
 * {@code Inventory.reserve(qty, reservationId)}, persist the Movement rows
 * and the new {@link Reservation} aggregate, and write one outbox row.
 *
 * <p>Optimistic-lock retry: if any row's version-checked UPDATE conflicts,
 * the transaction rolls back and the entire reserve attempt is retried up to
 * 3 times with 100–300ms jitter. After exhaustion, the original
 * {@link OptimisticLockingFailureException} bubbles up and the global handler
 * maps it to {@code 409 CONFLICT}.
 *
 * <p>Idempotency: if {@code pickingRequestId} already has a Reservation in
 * the DB, the existing record is returned (consistent with the contract's
 * "same body replayed" replay semantics). If the body differs, the persistence
 * adapter throws {@link DuplicateRequestException}.
 */
@Service
public class ReserveStockService implements ReserveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long JITTER_MIN_MS = 100L;
    private static final long JITTER_MAX_MS = 300L;

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Counter reserveCounter;
    private final Counter retryCounter;

    public ReserveStockService(ReservationRepository reservationRepository,
                               InventoryRepository inventoryRepository,
                               InventoryMovementRepository movementRepository,
                               OutboxWriter outboxWriter,
                               TransactionTemplate transactionTemplate,
                               Clock clock,
                               MeterRegistry meterRegistry) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.outboxWriter = outboxWriter;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.reserveCounter = Counter.builder("inventory.mutation.count")
                .tag("operation", "RESERVE")
                .description("Successful inventory mutations by operation")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("inventory.optimistic-lock.retry.count")
                .tag("operation", "RESERVE")
                .description("Optimistic-lock retries by operation")
                .register(meterRegistry);
    }

    @Override
    public ReservationView reserve(ReserveStockCommand command) {
        // Idempotency: if a Reservation already exists for this pickingRequestId,
        // assume the body has been verified upstream (REST IdempotencyFilter
        // handles body-hash mismatches; consumer paths use eventId dedupe).
        var existing = reservationRepository.findByPickingRequestId(command.pickingRequestId());
        if (existing.isPresent()) {
            log.debug("Reservation already exists for pickingRequestId {}; returning cached",
                    command.pickingRequestId());
            return ReservationView.from(existing.get());
        }

        OptimisticLockingFailureException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> doReserve(command));
            } catch (OptimisticLockingFailureException conflict) {
                lastConflict = conflict;
                retryCounter.increment();
                if (attempt < MAX_ATTEMPTS) {
                    sleepWithJitter();
                    log.info("Reserve attempt {} for pickingRequestId {} hit optimistic lock; retrying",
                            attempt, command.pickingRequestId());
                }
            }
        }
        log.warn("Reserve for pickingRequestId {} exhausted {} retries", command.pickingRequestId(), MAX_ATTEMPTS);
        throw lastConflict;
    }

    private ReservationView doReserve(ReserveStockCommand command) {
        Instant now = clock.instant();

        // Load Inventory rows first, in deterministic id-ascending order to
        // ensure a stable lock acquisition sequence under concurrent inverse
        // reserves on overlapping rows.
        List<UUID> orderedIds = command.lines().stream()
                .map(ReserveStockCommand.Line::inventoryId)
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<UUID, Integer> qtyByInventory = new HashMap<>();
        for (ReserveStockCommand.Line line : command.lines()) {
            qtyByInventory.merge(line.inventoryId(), line.quantity(), Integer::sum);
        }

        Map<UUID, Inventory> loadedById = new HashMap<>();
        for (UUID id : orderedIds) {
            Inventory inv = inventoryRepository.findById(id)
                    .orElseThrow(() -> new InventoryNotFoundException("Inventory not found: " + id));
            loadedById.put(id, inv);
        }

        UUID reservationId = UUID.randomUUID();
        List<ReservationLine> reservationLines = new ArrayList<>();
        List<InventoryReservedEvent.Line> eventLines = new ArrayList<>();

        for (ReserveStockCommand.Line cmdLine : command.lines()) {
            Inventory inv = loadedById.get(cmdLine.inventoryId());
            List<InventoryMovement> movements = inv.reserve(
                    cmdLine.quantity(), reservationId, ReasonCode.PICKING,
                    command.sourceEventId(), command.actorId(), now);
            inventoryRepository.updateWithVersionCheck(inv);
            movements.forEach(movementRepository::save);

            UUID lineId = UUID.randomUUID();
            ReservationLine line = new ReservationLine(
                    lineId, reservationId, inv.id(), inv.locationId(),
                    inv.skuId(), inv.lotId(), cmdLine.quantity());
            reservationLines.add(line);

            eventLines.add(new InventoryReservedEvent.Line(
                    lineId, inv.id(), inv.locationId(), inv.skuId(), inv.lotId(),
                    cmdLine.quantity(), inv.availableQty(), inv.reservedQty()));
        }

        Instant expiresAt = now.plusSeconds(command.ttlSeconds());
        Reservation reservation = Reservation.create(
                reservationId, command.pickingRequestId(), command.warehouseId(),
                reservationLines, expiresAt, now, command.actorId());
        Reservation persisted = reservationRepository.insert(reservation);

        outboxWriter.write(new InventoryReservedEvent(
                reservation.id(), reservation.pickingRequestId(), reservation.warehouseId(),
                expiresAt, eventLines, now, command.actorId()));
        reserveCounter.increment();
        log.info("inventory.reserved emitted reservationId={} lines={}",
                reservation.id(), eventLines.size());
        return ReservationView.from(persisted);
    }

    private void sleepWithJitter() {
        long jitter = ThreadLocalRandom.current().nextLong(JITTER_MIN_MS, JITTER_MAX_MS + 1);
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
