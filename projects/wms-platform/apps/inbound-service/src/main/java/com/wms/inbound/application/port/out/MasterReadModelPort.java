package com.wms.inbound.application.port.out;

import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import com.wms.inbound.domain.model.masterref.LotSnapshot;
import com.wms.inbound.domain.model.masterref.PartnerSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import com.wms.inbound.domain.model.masterref.WarehouseSnapshot;
import com.wms.inbound.domain.model.masterref.ZoneSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port over the local read-model cache populated by {@code master.*}
 * consumers.
 *
 * <p>The use-case layer queries this port to validate that a mutation targets
 * an {@code ACTIVE} resource and to enrich query responses with display fields.
 * Returns {@link Optional#empty()} when the snapshot has not yet been populated
 * (startup race) — the caller decides whether to fail or fall back per the
 * relevant spec rule.
 *
 * <p>Mutating writes to the underlying tables happen exclusively through
 * {@link MasterReadModelWriterPort}, which is invoked by the master consumers.
 */
public interface MasterReadModelPort {

    Optional<WarehouseSnapshot> findWarehouse(UUID id);

    Optional<WarehouseSnapshot> findWarehouseByCode(String warehouseCode);

    Optional<ZoneSnapshot> findZone(UUID id);

    Optional<LocationSnapshot> findLocation(UUID id);

    Optional<SkuSnapshot> findSku(UUID id);

    Optional<SkuSnapshot> findSkuByCode(String skuCode);

    Optional<LotSnapshot> findLot(UUID id);

    Optional<LotSnapshot> findLotBySkuAndLotNo(UUID skuId, String lotNo);

    Optional<PartnerSnapshot> findPartner(UUID id);

    Optional<PartnerSnapshot> findPartnerByCode(String partnerCode);
}
