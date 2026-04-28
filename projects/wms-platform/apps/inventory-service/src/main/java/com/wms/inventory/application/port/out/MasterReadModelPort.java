package com.wms.inventory.application.port.out;

import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port over the local read-model cache populated by {@code master.*}
 * consumers.
 *
 * <p>The use-case layer queries this port to validate that a mutation targets
 * an {@code ACTIVE} Location / SKU / Lot and to enrich query responses with
 * display fields. Returns {@link Optional#empty()} when the snapshot has not
 * yet been populated (startup race) — the caller decides whether to fail or
 * fall back per the relevant spec rule.
 *
 * <p>Mutating writes to the underlying tables happen exclusively through
 * {@link MasterReadModelWriterPort}, which is invoked by the master consumers.
 */
public interface MasterReadModelPort {

    Optional<LocationSnapshot> findLocation(UUID id);

    Optional<SkuSnapshot> findSku(UUID id);

    Optional<LotSnapshot> findLot(UUID id);
}
