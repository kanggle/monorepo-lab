package com.wms.inventory.application.service;

import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.event.InventoryReserveFailedEvent;
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
        // Idempotency (two layers): the shared IdempotencyKeyFilter (wired in
        // IdempotencyConfig, TASK-BE-505) short-circuits same-Idempotency-Key
        // replays and rejects same-key/different-body with 409 before this method
        // runs; a same pickingRequestId that still reaches here (e.g. a distinct
        // key, or the consumer path which uses eventId dedupe, not the header)
        // is treated as an idempotent hit and the existing Reservation replayed.
        var existing = reservationRepository.findByPickingRequestId(command.pickingRequestId());
        if (existing.isPresent()) {
            log.debug("Reservation already exists for pickingRequestId {}; returning cached",
                    command.pickingRequestId());
            return ReservationView.from(existing.get());
        }
        // REST path: a shortfall throws InsufficientStockException (→ 422).
        return withRetry(command, () -> transactionTemplate.execute(status -> doReserve(command, false)));
    }

    @Override
    public ReserveOutcome reserveForPickingEvent(ReserveStockCommand command) {
        // Idempotent replay: an existing reservation means this picking request
        // already reserved successfully — RESERVED, no re-emit.
        var existing = reservationRepository.findByPickingRequestId(command.pickingRequestId());
        if (existing.isPresent()) {
            log.debug("Reservation already exists for pickingRequestId {}; idempotent RESERVED",
                    command.pickingRequestId());
            return ReserveOutcome.RESERVED;
        }
        // Event path: a shortfall emits inventory.reserve.failed and returns null
        // (→ BACKORDERED) instead of throwing — so the consumer TX commits and the
        // dedupe row is retained (no DLT / redelivery loop).
        ReservationView view = withRetry(command,
                () -> transactionTemplate.execute(status -> doReserve(command, true)));
        return view == null ? ReserveOutcome.BACKORDERED : ReserveOutcome.RESERVED;
    }

    @Override
    public void signalReserveFailed(UUID pickingRequestId, String actorId,
                                    List<ReserveStockUseCase.Shortfall> shortfalls) {
        if (shortfalls == null || shortfalls.isEmpty()) {
            throw new IllegalArgumentException("signalReserveFailed requires at least one shortfall line");
        }
        // Idempotent replay: if a Reservation already exists for this picking
        // request it reserved successfully on an earlier delivery — do not emit
        // a contradictory reserve.failed.
        if (reservationRepository.findByPickingRequestId(pickingRequestId).isPresent()) {
            log.debug("Reservation already exists for pickingRequestId {}; skipping reserve.failed signal",
                    pickingRequestId);
            return;
        }
        Instant now = clock.instant();
        List<InventoryReserveFailedEvent.Line> lines = shortfalls.stream()
                .map(s -> new InventoryReserveFailedEvent.Line(
                        s.inventoryId(), s.skuId(), s.lotId(), s.locationId(),
                        s.qtyRequested(), s.qtyAvailable()))
                .toList();
        transactionTemplate.executeWithoutResult(status ->
                outboxWriter.write(new InventoryReserveFailedEvent(
                        pickingRequestId, "INSUFFICIENT_STOCK", lines, now, actorId)));
        log.info("inventory.reserve.failed emitted (unresolved natural key) pickingRequestId={} shortLines={}",
                pickingRequestId, lines.size());
    }

    /** Optimistic-lock retry wrapper shared by the REST and event reserve paths. */
    private ReservationView withRetry(ReserveStockCommand command,
                                      java.util.function.Supplier<ReservationView> body) {
        OptimisticLockingFailureException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return body.get();
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

    /**
     * @param signalShortfall when {@code true} (event path), a pre-checked
     *        availability shortfall emits {@code inventory.reserve.failed} and
     *        returns {@code null} (→ BACKORDERED) rather than letting
     *        {@code Inventory.reserve} throw (which would poison the shared TX and
     *        DLT the message). When {@code false} (REST path), the shortfall path
     *        is skipped and {@code Inventory.reserve} throws as before.
     */
    private ReservationView doReserve(ReserveStockCommand command, boolean signalShortfall) {
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

        // Event path (TASK-MONO-196): pre-check availability BEFORE any mutation so
        // a deterministic shortfall emits inventory.reserve.failed instead of
        // letting Inventory.reserve throw (which would mark the shared consumer TX
        // rollback-only — the outbox row + dedupe could not then commit). A
        // genuine concurrent race still throws below → optimistic-lock retry → the
        // re-check on retry sees the depletion and emits the failure event.
        if (signalShortfall) {
            List<InventoryReserveFailedEvent.Line> shortfall = new ArrayList<>();
            for (Map.Entry<UUID, Integer> entry : qtyByInventory.entrySet()) {
                Inventory inv = loadedById.get(entry.getKey());
                if (inv.availableQty() < entry.getValue()) {
                    shortfall.add(new InventoryReserveFailedEvent.Line(
                            inv.id(), inv.skuId(), inv.lotId(), inv.locationId(),
                            entry.getValue(), inv.availableQty()));
                }
            }
            if (!shortfall.isEmpty()) {
                outboxWriter.write(new InventoryReserveFailedEvent(
                        command.pickingRequestId(), "INSUFFICIENT_STOCK", shortfall, now, command.actorId()));
                log.info("inventory.reserve.failed emitted pickingRequestId={} shortLines={}",
                        command.pickingRequestId(), shortfall.size());
                return null;
            }
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
