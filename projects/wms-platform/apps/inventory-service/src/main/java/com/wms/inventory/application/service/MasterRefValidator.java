package com.wms.inventory.application.service;

import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.domain.exception.MasterRefInactiveException;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared validator for the 3-stage master-data activeness check
 * (Location active → SKU active → Lot active/not-expired). Extracted from
 * {@link AdjustStockService#doAdjust} and {@link ReceiveStockService} to
 * eliminate the 24-line byte-identical block duplicated across both services
 * (TASK-BE-301 Cohort A closure).
 *
 * <p>Lot check is skipped when {@code lotId} is {@code null} (non-LOT-tracked
 * SKU). Snapshot absence (Optional empty) is treated as "no opinion" — the
 * validator does not throw on missing snapshots because the master-read-model
 * is eventually consistent and absence does not imply inactive.
 *
 * <p>The companion {@link com.wms.inventory.application.service.TransferStockService}
 * has a similar but distinct {@code validateSku} method that additionally
 * enforces SKU {@code requiresLot} semantics — that helper is intentionally
 * not extracted here because its responsibilities are wider than this 3-stage
 * activeness check.
 */
@Component
public class MasterRefValidator {

    private final MasterReadModelPort masterReadModel;

    public MasterRefValidator(MasterReadModelPort masterReadModel) {
        this.masterReadModel = masterReadModel;
    }

    /**
     * @param locationId required — Location active check
     * @param skuId      required — SKU active check
     * @param lotId      nullable — when present, Lot expired-then-active check
     */
    public void validate(UUID locationId, UUID skuId, UUID lotId) {
        Optional<LocationSnapshot> location = masterReadModel.findLocation(locationId);
        location.ifPresent(s -> {
            if (!s.isActive()) {
                throw MasterRefInactiveException.locationInactive(s.id().toString());
            }
        });
        Optional<SkuSnapshot> sku = masterReadModel.findSku(skuId);
        sku.ifPresent(s -> {
            if (!s.isActive()) {
                throw MasterRefInactiveException.skuInactive(s.id().toString());
            }
        });
        if (lotId != null) {
            Optional<LotSnapshot> lot = masterReadModel.findLot(lotId);
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
}
