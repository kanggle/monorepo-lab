package com.wms.inbound.application.port.out;

import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import com.wms.inbound.domain.model.masterref.LotSnapshot;
import com.wms.inbound.domain.model.masterref.PartnerSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import com.wms.inbound.domain.model.masterref.WarehouseSnapshot;
import com.wms.inbound.domain.model.masterref.ZoneSnapshot;

/**
 * Out-port for upserting master read-model snapshots.
 *
 * <p>Used exclusively by the master consumers. The version guard logic — drop
 * events whose {@code master_version} is less than or equal to the cached
 * value — is enforced at the SQL layer via a conditional UPDATE so the check
 * and the write are atomic.
 *
 * <p>Each method returns {@code true} if the upsert applied, {@code false} if
 * the cached row's {@code masterVersion} is greater than or equal (out-of-order
 * older event arriving late).
 */
public interface MasterReadModelWriterPort {

    boolean upsertWarehouse(WarehouseSnapshot snapshot);

    boolean upsertZone(ZoneSnapshot snapshot);

    boolean upsertLocation(LocationSnapshot snapshot);

    boolean upsertSku(SkuSnapshot snapshot);

    boolean upsertLot(LotSnapshot snapshot);

    boolean upsertPartner(PartnerSnapshot snapshot);
}
