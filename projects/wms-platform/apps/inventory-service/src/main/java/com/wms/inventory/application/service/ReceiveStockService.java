package com.wms.inventory.application.service;

import com.wms.inventory.application.command.ReceiveStockCommand;
import com.wms.inventory.application.command.ReceiveStockLineCommand;
import com.wms.inventory.application.port.in.ReceiveStockUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.domain.event.InventoryReceivedEvent;
import com.wms.inventory.domain.exception.MasterRefInactiveException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.WarehouseSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the receive flow.
 *
 * <p>For each line of the consumed putaway event:
 * <ol>
 *   <li>Validate the {@code locationId} / {@code skuId} / {@code lotId} against
 *       the {@code MasterReadModel}. If a snapshot is present and {@code !ACTIVE},
 *       throw the matching {@link MasterRefInactiveException}. If the snapshot
 *       is absent, allow the receive — startup-race tolerance documented in
 *       {@code domain-model.md} §1 / §8.</li>
 *   <li>Upsert the Inventory row at {@code (locationId, skuId, lotId)}.</li>
 *   <li>Call {@code Inventory.receive(qty)}, persist the new aggregate state
 *       and the resulting Movement row.</li>
 * </ol>
 *
 * <p>After all lines commit, write one {@link InventoryReceivedEvent} to the
 * outbox carrying every line's state.
 *
 * <p>The whole method runs inside one transaction. If any line throws, the
 * entire batch rolls back including the EventDedupe row, allowing a retry
 * after the transient cause is addressed.
 */
@Service
public class ReceiveStockService implements ReceiveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReceiveStockService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxWriter outboxWriter;
    private final MasterReadModelPort masterReadModel;
    private final MasterRefValidator masterRefValidator;
    private final Clock clock;
    private final Counter receiveCounter;

    public ReceiveStockService(InventoryRepository inventoryRepository,
                               InventoryMovementRepository movementRepository,
                               OutboxWriter outboxWriter,
                               MasterReadModelPort masterReadModel,
                               MasterRefValidator masterRefValidator,
                               Clock clock,
                               MeterRegistry meterRegistry) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.outboxWriter = outboxWriter;
        this.masterReadModel = masterReadModel;
        this.masterRefValidator = masterRefValidator;
        this.clock = clock;
        this.receiveCounter = Counter.builder("inventory.mutation.count")
                .tag("operation", "RECEIVE")
                .description("Successful inventory mutations by operation")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public void receive(ReceiveStockCommand command) {
        Instant now = clock.instant();
        String actorId = command.actorId();
        List<InventoryReceivedEvent.Line> eventLines = new ArrayList<>(command.lines().size());

        for (ReceiveStockLineCommand line : command.lines()) {
            masterRefValidator.validate(line.locationId(), line.skuId(), line.lotId());

            Optional<Inventory> existing = inventoryRepository
                    .findByKey(line.locationId(), line.skuId(), line.lotId());
            Inventory inventory = existing
                    .orElseGet(() -> createNewRow(line, command.warehouseId(), now, actorId));

            InventoryMovement movement = inventory.receive(
                    line.qtyReceived(), ReasonCode.PUTAWAY,
                    command.sourceEventId(), actorId, now);

            Inventory persisted = existing.isPresent()
                    ? inventoryRepository.updateWithVersionCheck(inventory)
                    : inventoryRepository.insert(inventory);
            movementRepository.save(movement);
            receiveCounter.increment();

            String locationCode = masterReadModel.findLocation(line.locationId())
                    .map(LocationSnapshot::locationCode)
                    .orElse(null);
            eventLines.add(new InventoryReceivedEvent.Line(
                    persisted.id(),
                    line.locationId(),
                    locationCode,
                    line.skuId(),
                    line.lotId(),
                    line.qtyReceived(),
                    persisted.availableQty()));
        }

        // ADR-MONO-050 D9: carry the warehouse CODE so the cross-project scm batch
        // replenishment leg can address the PO by code. Best-effort — null when the
        // warehouse snapshot is not yet populated; the event still fires.
        String warehouseCode = masterReadModel.findWarehouse(command.warehouseId())
                .map(WarehouseSnapshot::warehouseCode).orElse(null);
        InventoryReceivedEvent event = new InventoryReceivedEvent(
                command.warehouseId(),
                warehouseCode,
                command.sourceEventId(),
                command.asnId(),
                eventLines,
                now,
                actorId);
        outboxWriter.write(event);
        log.info("inventory.received emitted for sourceEventId={} lines={}",
                command.sourceEventId(), eventLines.size());
    }

    private Inventory createNewRow(ReceiveStockLineCommand line, UUID warehouseId,
                                   Instant now, String actorId) {
        return Inventory.createEmpty(
                UUID.randomUUID(), warehouseId,
                line.locationId(), line.skuId(), line.lotId(),
                now, actorId);
    }


}
