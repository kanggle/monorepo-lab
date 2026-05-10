package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.ZoneSnapshot;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects {@code wms.master.zone.v1} events into the local
 * {@link ZoneSnapshot} read-model.
 */
@Component
class MasterZoneProjector implements MasterEventProjector {

    private static final Logger log = LoggerFactory.getLogger(MasterZoneProjector.class);

    private final MasterReadModelWriterPort writer;
    private final Clock clock;

    MasterZoneProjector(MasterReadModelWriterPort writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void apply(EventEnvelope envelope) {
        JsonNode zone = envelope.payload().get("zone");
        if (zone == null || zone.isNull()) {
            throw new IllegalArgumentException(
                    "master.zone event missing payload.zone: " + envelope.eventType());
        }
        ZoneSnapshot snapshot = new ZoneSnapshot(
                UUID.fromString(zone.get("id").asText()),
                UUID.fromString(zone.get("warehouseId").asText()),
                zone.get("zoneCode").asText(),
                zone.get("zoneType").asText(),
                ZoneSnapshot.Status.valueOf(zone.get("status").asText()),
                clock.instant(),
                zone.get("version").asLong());
        boolean applied = writer.upsertZone(snapshot);
        if (!applied) {
            log.debug("master.zone {} arrived stale (master_version={}); dropped",
                    snapshot.id(), snapshot.masterVersion());
        }
    }
}
