package com.wms.inventory.application.service;

import com.wms.inventory.application.command.AdjustStockCommand;
import com.wms.inventory.application.port.in.AdjustStockUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.StockAdjustmentRepository;
import com.wms.inventory.application.result.AdjustmentResult;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.domain.event.InventoryAdjustedEvent;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.MasterRefInactiveException;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.StockAdjustment;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual adjustment / mark-damaged / write-off-damaged.
 *
 * <p>One transactional path covers all three operations because they share
 * the persistence shape: load Inventory → mutate → persist Inventory +
 * Movement(s) + StockAdjustment + Outbox + low-stock detection.
 *
 * <p><b>Authorization (per architecture.md §Security):</b> authorization
 * decisions live in the application layer. Adjustments that target the
 * {@code RESERVED} bucket additionally require the caller to hold
 * {@code ROLE_INVENTORY_ADMIN}; the guard is enforced inside
 * {@link #doAdjust} based on {@link AdjustStockCommand#callerRoles()}, which
 * adapters populate from the authenticated principal. Throws
 * {@link AccessDeniedException} on denial — mapped to HTTP 403 by
 * {@code GlobalExceptionHandler}.
 */
@Service
public class AdjustStockService implements AdjustStockUseCase {

    private static final String ROLE_INVENTORY_ADMIN = "ROLE_INVENTORY_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(AdjustStockService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final OutboxWriter outboxWriter;
    private final MasterReadModelPort masterReadModel;
    private final LowStockDetectionService lowStockDetection;
    private final Clock clock;
    private final Counter adjustCounter;
    private final Counter markDamagedCounter;
    private final Counter writeOffCounter;

    public AdjustStockService(InventoryRepository inventoryRepository,
                              InventoryMovementRepository movementRepository,
                              StockAdjustmentRepository adjustmentRepository,
                              OutboxWriter outboxWriter,
                              MasterReadModelPort masterReadModel,
                              LowStockDetectionService lowStockDetection,
                              Clock clock,
                              MeterRegistry meterRegistry) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.outboxWriter = outboxWriter;
        this.masterReadModel = masterReadModel;
        this.lowStockDetection = lowStockDetection;
        this.clock = clock;
        this.adjustCounter = counter(meterRegistry, "ADJUST");
        this.markDamagedCounter = counter(meterRegistry, "MARK_DAMAGED");
        this.writeOffCounter = counter(meterRegistry, "WRITE_OFF_DAMAGED");
    }

    @Override
    @Transactional
    public AdjustmentResult adjust(AdjustStockCommand command) {
        Instant now = clock.instant();
        Inventory inventory = inventoryRepository.findById(command.inventoryId())
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found: " + command.inventoryId()));
        validateMasterRefs(inventory);

        return switch (command.operation()) {
            case REGULAR -> doAdjust(command, inventory, now);
            case MARK_DAMAGED -> doMarkDamaged(command, inventory, now);
            case WRITE_OFF_DAMAGED -> doWriteOffDamaged(command, inventory, now);
        };
    }

    private AdjustmentResult doAdjust(AdjustStockCommand command, Inventory inventory, Instant now) {
        if (command.bucket() == null) {
            throw new InventoryValidationException("bucket is required for regular adjustment");
        }
        if (command.reasonCode() == null) {
            throw new InventoryValidationException("reasonCode is required for regular adjustment");
        }
        if (command.delta() == 0) {
            throw new InventoryValidationException("delta must be non-zero");
        }
        if (command.bucket() == Bucket.RESERVED
                && !command.callerRoles().contains(ROLE_INVENTORY_ADMIN)) {
            throw new AccessDeniedException(
                    "RESERVED-bucket adjustment requires INVENTORY_ADMIN");
        }
        StockAdjustment adjustment = StockAdjustment.create(
                inventory.id(), command.bucket(), command.delta(),
                command.reasonCode(), command.reasonNote(),
                command.actorId(), command.idempotencyKey(), now);
        InventoryMovement movement = inventory.adjust(
                command.delta(), command.bucket(), command.reasonCode(),
                command.reasonNote(), adjustment.id(), command.actorId(), now);
        return persistAdjustmentResult(adjustment, movement, inventory,
                MovementType.ADJUSTMENT, command.delta(), command.bucket(),
                command.reasonCode(), command.reasonNote(),
                command.delta() < 0, now, command.actorId(), adjustCounter);
    }

    private AdjustmentResult doMarkDamaged(AdjustStockCommand command, Inventory inventory, Instant now) {
        int qty = command.delta();
        if (qty <= 0) {
            throw new InventoryValidationException("mark-damaged quantity must be > 0");
        }
        String reasonNote = command.reasonNote();
        StockAdjustment adjustment = StockAdjustment.create(
                inventory.id(), Bucket.AVAILABLE, -qty,
                ReasonCode.ADJUSTMENT_DAMAGE, reasonNote,
                command.actorId(), command.idempotencyKey(), now);
        var movements = inventory.markDamaged(qty, reasonNote, adjustment.id(),
                command.actorId(), now);
        adjustmentRepository.insert(adjustment);
        inventoryRepository.updateWithVersionCheck(inventory);
        movements.forEach(movementRepository::save);
        outboxWriter.write(buildEvent(adjustment, inventory, MovementType.DAMAGE_MARK,
                -qty, Bucket.AVAILABLE, ReasonCode.ADJUSTMENT_DAMAGE, reasonNote,
                now, command.actorId()));
        markDamagedCounter.increment();
        lowStockDetection.evaluate(inventory, "inventory.adjusted", null, now, command.actorId());
        log.info("inventory.adjusted emitted (DAMAGE_MARK) adjustmentId={} inventoryId={}",
                adjustment.id(), inventory.id());
        return result(adjustment, inventory);
    }

    private AdjustmentResult doWriteOffDamaged(AdjustStockCommand command, Inventory inventory, Instant now) {
        int qty = command.delta();
        if (qty <= 0) {
            throw new InventoryValidationException("write-off quantity must be > 0");
        }
        String reasonNote = command.reasonNote();
        StockAdjustment adjustment = StockAdjustment.create(
                inventory.id(), Bucket.DAMAGED, -qty,
                ReasonCode.DAMAGE_WRITE_OFF, reasonNote,
                command.actorId(), command.idempotencyKey(), now);
        InventoryMovement movement = inventory.writeOffDamaged(qty, reasonNote,
                adjustment.id(), command.actorId(), now);
        adjustmentRepository.insert(adjustment);
        inventoryRepository.updateWithVersionCheck(inventory);
        movementRepository.save(movement);
        outboxWriter.write(buildEvent(adjustment, inventory, MovementType.DAMAGE_WRITE_OFF,
                -qty, Bucket.DAMAGED, ReasonCode.DAMAGE_WRITE_OFF, reasonNote,
                now, command.actorId()));
        writeOffCounter.increment();
        log.info("inventory.adjusted emitted (DAMAGE_WRITE_OFF) adjustmentId={} inventoryId={}",
                adjustment.id(), inventory.id());
        return result(adjustment, inventory);
    }

    private AdjustmentResult persistAdjustmentResult(StockAdjustment adjustment,
                                                     InventoryMovement movement,
                                                     Inventory inventory,
                                                     MovementType movementType,
                                                     int delta, Bucket bucket,
                                                     ReasonCode reasonCode, String reasonNote,
                                                     boolean reducedAvailable,
                                                     Instant now, String actorId,
                                                     Counter counter) {
        adjustmentRepository.insert(adjustment);
        inventoryRepository.updateWithVersionCheck(inventory);
        movementRepository.save(movement);
        outboxWriter.write(buildEvent(adjustment, inventory, movementType,
                delta, bucket, reasonCode, reasonNote, now, actorId));
        counter.increment();
        if (reducedAvailable && bucket == Bucket.AVAILABLE) {
            lowStockDetection.evaluate(inventory, "inventory.adjusted", null, now, actorId);
        }
        log.info("inventory.adjusted emitted ({}) adjustmentId={} inventoryId={}",
                movementType, adjustment.id(), inventory.id());
        return result(adjustment, inventory);
    }

    private static InventoryAdjustedEvent buildEvent(StockAdjustment adjustment, Inventory inventory,
                                                     MovementType movementType,
                                                     int delta, Bucket bucket,
                                                     ReasonCode reasonCode, String reasonNote,
                                                     Instant now, String actorId) {
        return new InventoryAdjustedEvent(
                adjustment.id(), inventory.id(),
                inventory.locationId(), inventory.skuId(), inventory.lotId(),
                bucket, delta, reasonCode, reasonNote, movementType,
                new InventoryAdjustedEvent.InventorySnapshot(
                        inventory.availableQty(), inventory.reservedQty(),
                        inventory.damagedQty(), inventory.onHandQty(),
                        inventory.version()),
                now, actorId);
    }

    private static AdjustmentResult result(StockAdjustment adjustment, Inventory inventory) {
        return new AdjustmentResult(
                AdjustmentView.from(adjustment),
                new AdjustmentResult.InventorySnapshot(
                        inventory.id(),
                        inventory.availableQty(), inventory.reservedQty(),
                        inventory.damagedQty(), inventory.onHandQty(),
                        inventory.version()));
    }

    private void validateMasterRefs(Inventory inventory) {
        Optional<LocationSnapshot> location = masterReadModel.findLocation(inventory.locationId());
        location.ifPresent(s -> {
            if (!s.isActive()) {
                throw MasterRefInactiveException.locationInactive(s.id().toString());
            }
        });
        Optional<SkuSnapshot> sku = masterReadModel.findSku(inventory.skuId());
        sku.ifPresent(s -> {
            if (!s.isActive()) {
                throw MasterRefInactiveException.skuInactive(s.id().toString());
            }
        });
        if (inventory.lotId() != null) {
            Optional<LotSnapshot> lot = masterReadModel.findLot(inventory.lotId());
            lot.ifPresent(s -> {
                if (s.isExpired()) {
                    throw MasterRefInactiveException.lotExpired(s.id().toString());
                }
                if (!s.isActive()) {
                    throw MasterRefInactiveException.lotInactive(s.id().toString());
                }
            });
        }
    }

    private static Counter counter(MeterRegistry registry, String operation) {
        return Counter.builder("inventory.mutation.count")
                .tag("operation", operation)
                .description("Successful inventory mutations by operation")
                .register(registry);
    }
}
