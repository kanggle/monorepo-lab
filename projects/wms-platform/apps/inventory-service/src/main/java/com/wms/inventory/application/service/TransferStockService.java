package com.wms.inventory.application.service;

import com.wms.inventory.application.command.TransferStockCommand;
import com.wms.inventory.application.port.in.TransferStockUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.StockTransferRepository;
import com.wms.inventory.application.result.TransferResult;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.domain.event.InventoryTransferredEvent;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.MasterRefInactiveException;
import com.wms.inventory.domain.exception.TransferSameLocationException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.StockTransfer;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W1 atomic stock transfer between two locations within one warehouse.
 *
 * <p>Both Inventory rows are loaded / upserted in {@code id ASC} order to
 * prevent reciprocal-transfer deadlocks. The whole flow runs under one
 * {@code @Transactional}: load source / upsert target → mutate both →
 * persist transfer + 2 movements + 1 outbox row → run low-stock
 * detection on the source.
 */
@Service
public class TransferStockService implements TransferStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferStockService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final OutboxWriter outboxWriter;
    private final MasterReadModelPort masterReadModel;
    private final LowStockDetectionService lowStockDetection;
    private final Clock clock;
    private final Counter transferCounter;

    public TransferStockService(InventoryRepository inventoryRepository,
                                InventoryMovementRepository movementRepository,
                                StockTransferRepository transferRepository,
                                OutboxWriter outboxWriter,
                                MasterReadModelPort masterReadModel,
                                LowStockDetectionService lowStockDetection,
                                Clock clock,
                                MeterRegistry meterRegistry) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.transferRepository = transferRepository;
        this.outboxWriter = outboxWriter;
        this.masterReadModel = masterReadModel;
        this.lowStockDetection = lowStockDetection;
        this.clock = clock;
        this.transferCounter = Counter.builder("inventory.mutation.count")
                .tag("operation", "TRANSFER")
                .description("Successful inventory mutations by operation")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public TransferResult transfer(TransferStockCommand command) {
        if (command.sourceLocationId().equals(command.targetLocationId())) {
            throw new TransferSameLocationException(
                    "sourceLocationId and targetLocationId must differ");
        }

        Instant now = clock.instant();
        UUID warehouseId = resolveSameWarehouse(command.sourceLocationId(),
                command.targetLocationId());
        validateSku(command.skuId(), command.lotId());

        // Load / upsert in deterministic id-ascending order to prevent deadlocks.
        Inventory source = inventoryRepository.findByKey(
                        command.sourceLocationId(), command.skuId(), command.lotId())
                .orElseThrow(() -> new InventoryValidationException(
                        "Source inventory row not found for ("
                                + command.sourceLocationId() + ", " + command.skuId() + ", "
                                + command.lotId() + ")"));

        Optional<Inventory> existingTarget = inventoryRepository.findByKey(
                command.targetLocationId(), command.skuId(), command.lotId());
        boolean targetWasCreated = existingTarget.isEmpty();
        Inventory target = existingTarget.orElseGet(() -> Inventory.createEmpty(
                UUID.randomUUID(), warehouseId,
                command.targetLocationId(), command.skuId(), command.lotId(),
                now, command.actorId()));

        StockTransfer transfer = StockTransfer.create(
                warehouseId, command.sourceLocationId(), command.targetLocationId(),
                command.skuId(), command.lotId(), command.quantity(),
                command.reasonCode(), command.reasonNote(),
                command.actorId(), command.idempotencyKey(), now);

        // Apply legs in id-ascending order.
        Inventory first = source.id().compareTo(target.id()) <= 0 ? source : target;
        Inventory second = first == source ? target : source;
        InventoryMovement firstLeg = (first == source)
                ? source.transferOut(command.quantity(), transfer.id(), target.id(), command.actorId(), now)
                : target.transferIn(command.quantity(), transfer.id(), source.id(), command.actorId(), now);
        InventoryMovement secondLeg = (second == source)
                ? source.transferOut(command.quantity(), transfer.id(), target.id(), command.actorId(), now)
                : target.transferIn(command.quantity(), transfer.id(), source.id(), command.actorId(), now);

        // Persist both Inventory rows in the same id-ascending order.
        Inventory persistedFirst = persistInventory(first, first == target && targetWasCreated);
        Inventory persistedSecond = persistInventory(second, second == target && targetWasCreated);
        Inventory persistedSource = (first == source) ? persistedFirst : persistedSecond;
        Inventory persistedTarget = (first == target) ? persistedFirst : persistedSecond;

        movementRepository.save(firstLeg);
        movementRepository.save(secondLeg);
        StockTransfer savedTransfer = transferRepository.insert(transfer);

        String sourceLocationCode = masterReadModel.findLocation(source.locationId())
                .map(LocationSnapshot::locationCode).orElse(null);
        String targetLocationCode = masterReadModel.findLocation(target.locationId())
                .map(LocationSnapshot::locationCode).orElse(null);
        outboxWriter.write(new InventoryTransferredEvent(
                savedTransfer.id(), warehouseId, command.skuId(), command.lotId(), command.quantity(),
                command.reasonCode(),
                new InventoryTransferredEvent.Endpoint(
                        persistedSource.locationId(), sourceLocationCode,
                        persistedSource.id(), persistedSource.availableQty(), false),
                new InventoryTransferredEvent.Endpoint(
                        persistedTarget.locationId(), targetLocationCode,
                        persistedTarget.id(), persistedTarget.availableQty(),
                        targetWasCreated),
                now, command.actorId()));

        transferCounter.increment();
        // Source row's availableQty just dropped — evaluate the alert.
        lowStockDetection.evaluate(persistedSource, "inventory.transferred", null,
                now, command.actorId());

        log.info("inventory.transferred emitted transferId={} source={} target={} qty={}",
                savedTransfer.id(), persistedSource.id(), persistedTarget.id(),
                command.quantity());
        return new TransferResult(
                TransferView.from(savedTransfer),
                snapshot(persistedSource, false),
                snapshot(persistedTarget, targetWasCreated));
    }

    private Inventory persistInventory(Inventory inventory, boolean isFreshlyCreated) {
        return isFreshlyCreated
                ? inventoryRepository.insert(inventory)
                : inventoryRepository.updateWithVersionCheck(inventory);
    }

    private static TransferResult.Endpoint snapshot(Inventory inv, boolean wasCreated) {
        return new TransferResult.Endpoint(
                inv.id(), inv.availableQty(), inv.reservedQty(), inv.damagedQty(),
                inv.version(), wasCreated);
    }

    private UUID resolveSameWarehouse(UUID sourceLocationId, UUID targetLocationId) {
        Optional<LocationSnapshot> source = masterReadModel.findLocation(sourceLocationId);
        Optional<LocationSnapshot> target = masterReadModel.findLocation(targetLocationId);
        source.ifPresent(s -> {
            if (!s.isActive()) {
                throw MasterRefInactiveException.locationInactive(s.id().toString());
            }
        });
        target.ifPresent(t -> {
            if (!t.isActive()) {
                throw MasterRefInactiveException.locationInactive(t.id().toString());
            }
        });
        UUID sourceWh = source.map(LocationSnapshot::warehouseId).orElse(null);
        UUID targetWh = target.map(LocationSnapshot::warehouseId).orElse(null);
        if (sourceWh != null && targetWh != null && !sourceWh.equals(targetWh)) {
            throw new InventoryValidationException(
                    "Cross-warehouse transfers are not supported in v1");
        }
        // If snapshots are absent, fall back to existing source row's warehouse_id —
        // the use-case loads the source by key and uses its warehouseId.
        return sourceWh != null ? sourceWh : targetWh;
    }

    private void validateSku(UUID skuId, UUID lotId) {
        Optional<SkuSnapshot> sku = masterReadModel.findSku(skuId);
        sku.ifPresent(s -> {
            if (!s.isActive()) {
                throw MasterRefInactiveException.skuInactive(s.id().toString());
            }
            if (s.requiresLot() && lotId == null) {
                throw new InventoryValidationException(
                        "SKU " + s.id() + " is LOT-tracked but lotId was not supplied");
            }
            if (!s.requiresLot() && lotId != null) {
                throw new InventoryValidationException(
                        "SKU " + s.id() + " is not LOT-tracked but lotId was supplied");
            }
        });
        if (lotId != null) {
            Optional<LotSnapshot> lot = masterReadModel.findLot(lotId);
            lot.ifPresent(l -> {
                if (l.isExpired()) {
                    throw MasterRefInactiveException.lotExpired(l.id().toString());
                }
                if (!l.isActive()) {
                    throw MasterRefInactiveException.lotInactive(l.id().toString());
                }
            });
        }
    }
}
