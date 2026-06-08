package com.example.product.infrastructure.reconciliation;

import com.example.product.application.service.EventPublishingHelper;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.StockChangedPayload;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Core of ADR-MONO-022 §D4 v2(b) (Option B — delta reconciliation). Applies
 * warehouse-origin available-quantity deltas to ecommerce sellable stock, keeping the
 * order-time gate. See {@code specs/contracts/events/wms-inventory-subscriptions.md}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsInventoryReconciliationService {

    static final String RECONCILIATION_REASON = "WMS_RECONCILIATION";

    private final WmsSkuSnapshotRepository skuSnapshotRepository;
    private final WmsInventoryAvailableRepository inventoryAvailableRepository;
    private final InventoryRepository inventoryRepository;
    private final EventPublishingHelper eventPublishingHelper;
    private final Clock clock;

    /** Reverse-identity stream: upsert skuId → skuCode (version-guarded, out-of-order tolerant). */
    @Transactional
    public void upsertSkuSnapshot(UUID skuId, String skuCode, long version) {
        if (skuId == null || skuCode == null || skuCode.isBlank()) {
            log.warn("wms master.sku event missing skuId/skuCode — skipping. skuId={}", skuId);
            return;
        }
        Instant now = Instant.now(clock);
        skuSnapshotRepository.findById(skuId).ifPresentOrElse(existing -> {
            if (version < existing.getVersion()) {
                log.debug("Stale master.sku version, ignoring. skuId={}, in={}, stored={}",
                        skuId, version, existing.getVersion());
                return;
            }
            existing.update(skuCode, version, now);
            skuSnapshotRepository.save(existing);
        }, () -> skuSnapshotRepository.save(WmsSkuSnapshotEntity.of(skuId, skuCode, version, now)));
    }

    /**
     * Apply one warehouse-origin availableQty observation for an inventory row. First
     * sight establishes a baseline (delta 0, no phantom jump); later observations apply
     * {@code (new − stored)} to the mapped variant's stock.
     */
    @Transactional
    public void reconcileAvailable(UUID inventoryId, UUID skuId, int newAvailableQty) {
        if (inventoryId == null || skuId == null) {
            log.warn("wms inventory event missing inventoryId/skuId — skipping.");
            return;
        }

        Optional<WmsSkuSnapshotEntity> snapshot = skuSnapshotRepository.findById(skuId);
        if (snapshot.isEmpty()) {
            log.debug("No skuCode snapshot yet for skuId={} — skipping reconciliation (master.sku not arrived).", skuId);
            return;
        }
        String skuCode = snapshot.get().getSkuCode();

        Optional<InventoryRepository.VariantRef> variantRef = inventoryRepository.findVariantBySku(skuCode);
        if (variantRef.isEmpty()) {
            log.debug("skuCode={} maps to no ecommerce variant — skipping (wms SKU not sold here).", skuCode);
            return;
        }

        Instant now = Instant.now(clock);
        Optional<WmsInventoryAvailableEntity> ledger = inventoryAvailableRepository.findById(inventoryId);
        if (ledger.isEmpty()) {
            inventoryAvailableRepository.save(
                    WmsInventoryAvailableEntity.of(inventoryId, skuId, newAvailableQty, now));
            log.debug("Baseline established for inventoryId={} availableQty={} (delta 0).", inventoryId, newAvailableQty);
            return;
        }

        int delta = newAvailableQty - ledger.get().getAvailableQty();
        ledger.get().update(newAvailableQty, now);
        inventoryAvailableRepository.save(ledger.get());
        if (delta == 0) {
            return;
        }

        applyDeltaToVariant(variantRef.get(), delta, skuCode);
    }

    private void applyDeltaToVariant(InventoryRepository.VariantRef ref, int delta, String skuCode) {
        int previousStock = ref.currentStock();
        long target = (long) previousStock + delta;
        int newStock = target < 0 ? 0 : (int) target;
        if (target < 0) {
            log.warn("wms reconciliation would drive stock below 0 — clamping at 0. variantId={}, "
                    + "previous={}, delta={} (seed divergence beyond the delta window).",
                    ref.variantId(), previousStock, delta);
        }

        inventoryRepository.save(Inventory.create(ref.variantId(), new StockQuantity(newStock)));

        int appliedDelta = newStock - previousStock;
        eventPublishingHelper.publishSafely(
                ProductEvent.stockChanged(new StockChangedPayload(
                        ref.productId().toString(), ref.variantId().toString(),
                        previousStock, newStock, appliedDelta, RECONCILIATION_REASON, null)),
                "variant(wms-reconciliation)", ref.variantId());
        log.info("wms reconciliation applied. skuCode={}, variantId={}, {} -> {} (delta {}).",
                skuCode, ref.variantId(), previousStock, newStock, appliedDelta);
    }
}
