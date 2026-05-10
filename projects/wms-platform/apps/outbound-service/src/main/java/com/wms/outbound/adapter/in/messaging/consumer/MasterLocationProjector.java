package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.LocationSnapshot;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects {@code wms.master.location.v1} events into the local
 * {@link LocationSnapshot} read-model.
 */
@Component
class MasterLocationProjector implements MasterEventProjector {

    private static final Logger log = LoggerFactory.getLogger(MasterLocationProjector.class);

    private final MasterReadModelWriterPort writer;
    private final Clock clock;

    MasterLocationProjector(MasterReadModelWriterPort writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void apply(EventEnvelope envelope) {
        JsonNode location = envelope.payload().get("location");
        if (location == null || location.isNull()) {
            throw new IllegalArgumentException(
                    "master.location event missing payload.location: " + envelope.eventType());
        }
        LocationSnapshot snapshot = new LocationSnapshot(
                UUID.fromString(location.get("id").asText()),
                location.get("locationCode").asText(),
                UUID.fromString(location.get("warehouseId").asText()),
                UUID.fromString(location.get("zoneId").asText()),
                LocationSnapshot.LocationType.valueOf(location.get("locationType").asText()),
                LocationSnapshot.Status.valueOf(location.get("status").asText()),
                clock.instant(),
                location.get("version").asLong());
        boolean applied = writer.upsertLocation(snapshot);
        if (!applied) {
            log.debug("master.location {} arrived stale (master_version={}); dropped",
                    snapshot.id(), snapshot.masterVersion());
        }
    }
}
