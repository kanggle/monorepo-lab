package com.wms.inbound.adapter.in.messaging.masterref;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inbound.application.port.out.MasterReadModelWriterPort;
import com.wms.inbound.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects {@code wms.master.warehouse.v1} events into the local
 * {@link WarehouseSnapshot} read-model.
 */
@Component
class MasterWarehouseProjector implements MasterEventProjector {

    private static final Logger log = LoggerFactory.getLogger(MasterWarehouseProjector.class);

    private final MasterReadModelWriterPort writer;
    private final Clock clock;

    MasterWarehouseProjector(MasterReadModelWriterPort writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void apply(MasterEventEnvelope envelope) {
        JsonNode warehouse = envelope.payload().get("warehouse");
        if (warehouse == null || warehouse.isNull()) {
            throw new IllegalArgumentException(
                    "master.warehouse event missing payload.warehouse: " + envelope.eventType());
        }
        WarehouseSnapshot snapshot = new WarehouseSnapshot(
                UUID.fromString(warehouse.get("id").asText()),
                warehouse.get("warehouseCode").asText(),
                WarehouseSnapshot.Status.valueOf(warehouse.get("status").asText()),
                clock.instant(),
                warehouse.get("version").asLong());
        boolean applied = writer.upsertWarehouse(snapshot);
        if (!applied) {
            log.debug("master.warehouse {} arrived stale (master_version={}); dropped",
                    snapshot.id(), snapshot.masterVersion());
        }
    }
}
