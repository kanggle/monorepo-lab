package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.model.masterref.LocationSnapshot;
import com.wms.outbound.domain.model.masterref.LotSnapshot;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
import com.wms.outbound.domain.model.masterref.ZoneSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port over the local read-model cache populated by {@code master.*}
 * consumers.
 *
 * <p>The use-case layer queries this port to validate that a mutation targets
 * an {@code ACTIVE} resource and to enrich query responses with display fields.
 * Returns {@link Optional#empty()} when the snapshot has not yet been populated
 * (startup race) — the caller decides whether to fail or fall back.
 */
public interface MasterReadModelPort {

    Optional<WarehouseSnapshot> findWarehouse(UUID id);

    Optional<ZoneSnapshot> findZone(UUID id);

    Optional<LocationSnapshot> findLocation(UUID id);

    Optional<SkuSnapshot> findSku(UUID id);

    Optional<LotSnapshot> findLot(UUID id);

    Optional<PartnerSnapshot> findPartner(UUID id);
}
