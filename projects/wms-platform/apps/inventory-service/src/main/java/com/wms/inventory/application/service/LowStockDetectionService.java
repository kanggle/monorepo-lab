package com.wms.inventory.application.service;

import com.wms.inventory.application.port.out.LowStockAlertDebouncePort;
import com.wms.inventory.application.port.out.LowStockThresholdPort;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.domain.event.InventoryLowStockDetectedEvent;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Domain service responsible for emitting
 * {@link InventoryLowStockDetectedEvent} after a mutation has reduced
 * {@code availableQty}. Called inside the same {@code @Transactional}
 * boundary as the originating mutation so the alert outbox row commits
 * atomically.
 *
 * <p>Spec: {@code specs/services/inventory-service/architecture.md}
 * §Observability and {@code inventory-events.md} §7.
 *
 * <p>Decision flow:
 * <ol>
 *   <li>Resolve threshold via {@link LowStockThresholdPort#findThreshold}.
 *       Empty → no alert (detection disabled for this row).</li>
 *   <li>Compare {@code availableQtyAfter < threshold}. Not below → no alert.</li>
 *   <li>Consult {@link LowStockAlertDebouncePort#shouldFire}. Already fired
 *       within TTL → no alert.</li>
 *   <li>Write outbox row.</li>
 * </ol>
 */
@Service
public class LowStockDetectionService {

    private static final Logger log = LoggerFactory.getLogger(LowStockDetectionService.class);

    private final LowStockThresholdPort thresholdPort;
    private final LowStockAlertDebouncePort debouncePort;
    private final MasterReadModelPort masterReadModel;
    private final OutboxWriter outboxWriter;

    public LowStockDetectionService(LowStockThresholdPort thresholdPort,
                                    LowStockAlertDebouncePort debouncePort,
                                    MasterReadModelPort masterReadModel,
                                    OutboxWriter outboxWriter) {
        this.thresholdPort = thresholdPort;
        this.debouncePort = debouncePort;
        this.masterReadModel = masterReadModel;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Evaluate the post-mutation snapshot and emit an alert if the
     * threshold has been crossed and the debounce window allows it.
     *
     * @param inventory             the post-mutation aggregate
     * @param triggeringEventType   the {@code eventType} that caused the mutation
     *                              (e.g., {@code inventory.adjusted}); used for
     *                              telemetry — set as the {@code triggeringEventType}
     *                              field on the alert payload
     * @param triggeringEventId     the {@code eventId} of the triggering event;
     *                              {@code null} acceptable when none is available
     * @param now                   the enclosing transaction's clock instant
     * @param actorId               the JWT subject or {@code system:<consumer>}
     */
    public void evaluate(Inventory inventory, String triggeringEventType,
                         UUID triggeringEventId, Instant now, String actorId) {
        Optional<Integer> thresholdOpt = thresholdPort.findThreshold(
                inventory.warehouseId(), inventory.skuId());
        if (thresholdOpt.isEmpty()) {
            return;
        }
        int threshold = thresholdOpt.get();
        if (inventory.availableQty() >= threshold) {
            return;
        }
        if (!debouncePort.shouldFire(inventory.id())) {
            log.debug("Low-stock alert debounced for inventory {}", inventory.id());
            return;
        }
        String locationCode = masterReadModel.findLocation(inventory.locationId())
                .map(LocationSnapshot::locationCode).orElse(null);
        String skuCode = masterReadModel.findSku(inventory.skuId())
                .map(SkuSnapshot::skuCode).orElse(null);
        outboxWriter.write(new InventoryLowStockDetectedEvent(
                inventory.id(), inventory.locationId(), locationCode,
                inventory.skuId(), skuCode, inventory.lotId(),
                inventory.availableQty(), threshold,
                triggeringEventType, triggeringEventId, now, actorId));
        log.info("inventory.low-stock-detected emitted inventoryId={} available={} threshold={}",
                inventory.id(), inventory.availableQty(), threshold);
    }
}
