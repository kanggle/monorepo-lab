package com.wms.inventory.application.port.out;

import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;

/**
 * Out-port for upserting master read-model snapshots.
 *
 * <p>Used exclusively by the master consumers. The version guard logic — drop
 * events whose {@code master_version} is less than or equal to the cached
 * value — is enforced at the SQL layer via a conditional UPDATE so the check
 * and the write are atomic.
 */
public interface MasterReadModelWriterPort {

    /**
     * Upsert the supplied Location snapshot iff its {@code masterVersion} is
     * strictly greater than the currently cached value (or no row exists).
     *
     * @return {@code true} if the upsert applied, {@code false} if the cached
     *         row's {@code masterVersion} is greater or equal (out-of-order
     *         older event).
     */
    boolean upsertLocation(LocationSnapshot snapshot);

    boolean upsertSku(SkuSnapshot snapshot);

    boolean upsertLot(LotSnapshot snapshot);
}
