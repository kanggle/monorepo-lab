package com.wms.inbound.adapter.in.messaging.masterref;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inbound.application.port.out.MasterReadModelWriterPort;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects {@code wms.master.location.v1} events into the local
 * {@link LocationSnapshot} read-model.
 *
 * <p>Behavior contract:
 * <ul>
 *   <li>Out-of-order older events ({@code masterVersion} ≤ cached) are silently
 *       dropped by the SQL conditional UPDATE inside the upsert adapter.</li>
 *   <li>Status mapping follows the inbound payload's {@code status} value
 *       directly (master-service emits {@code ACTIVE} / {@code INACTIVE}).</li>
 * </ul>
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
    public void apply(MasterEventEnvelope envelope) {
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
