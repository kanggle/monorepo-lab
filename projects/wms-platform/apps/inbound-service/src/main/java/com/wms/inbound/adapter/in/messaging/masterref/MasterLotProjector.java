package com.wms.inbound.adapter.in.messaging.masterref;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inbound.application.port.out.MasterReadModelWriterPort;
import com.wms.inbound.domain.model.masterref.LotSnapshot;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects {@code wms.master.lot.v1} events into the local {@link LotSnapshot}
 * read-model. Lots flow through {@code ACTIVE}, {@code INACTIVE}, and
 * {@code EXPIRED}; the projector mirrors whichever status the inbound payload
 * carries.
 */
@Component
class MasterLotProjector implements MasterEventProjector {

    private static final Logger log = LoggerFactory.getLogger(MasterLotProjector.class);

    private final MasterReadModelWriterPort writer;
    private final Clock clock;

    MasterLotProjector(MasterReadModelWriterPort writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void apply(MasterEventEnvelope envelope) {
        JsonNode lot = envelope.payload().get("lot");
        if (lot == null || lot.isNull()) {
            throw new IllegalArgumentException(
                    "master.lot event missing payload.lot: " + envelope.eventType());
        }
        JsonNode expiry = lot.get("expiryDate");
        LocalDate expiryDate = (expiry == null || expiry.isNull())
                ? null : LocalDate.parse(expiry.asText());
        LotSnapshot snapshot = new LotSnapshot(
                UUID.fromString(lot.get("id").asText()),
                UUID.fromString(lot.get("skuId").asText()),
                lot.get("lotNo").asText(),
                expiryDate,
                LotSnapshot.Status.valueOf(lot.get("status").asText()),
                clock.instant(),
                lot.get("version").asLong());
        boolean applied = writer.upsertLot(snapshot);
        if (!applied) {
            log.debug("master.lot {} arrived stale (master_version={}); dropped",
                    snapshot.id(), snapshot.masterVersion());
        }
    }
}
