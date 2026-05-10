package com.wms.inbound.adapter.in.messaging.masterref;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inbound.application.port.out.MasterReadModelWriterPort;
import com.wms.inbound.domain.model.masterref.PartnerSnapshot;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects {@code wms.master.partner.v1} events into the local
 * {@link PartnerSnapshot} read-model. ASN supplier validation reads from this
 * snapshot via {@code MasterReadModelPort.findPartner(id)}.
 */
@Component
class MasterPartnerProjector implements MasterEventProjector {

    private static final Logger log = LoggerFactory.getLogger(MasterPartnerProjector.class);

    private final MasterReadModelWriterPort writer;
    private final Clock clock;

    MasterPartnerProjector(MasterReadModelWriterPort writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void apply(MasterEventEnvelope envelope) {
        JsonNode partner = envelope.payload().get("partner");
        if (partner == null || partner.isNull()) {
            throw new IllegalArgumentException(
                    "master.partner event missing payload.partner: " + envelope.eventType());
        }
        PartnerSnapshot snapshot = new PartnerSnapshot(
                UUID.fromString(partner.get("id").asText()),
                partner.get("partnerCode").asText(),
                PartnerSnapshot.PartnerType.valueOf(partner.get("partnerType").asText()),
                PartnerSnapshot.Status.valueOf(partner.get("status").asText()),
                clock.instant(),
                partner.get("version").asLong());
        boolean applied = writer.upsertPartner(snapshot);
        if (!applied) {
            log.debug("master.partner {} arrived stale (master_version={}); dropped",
                    snapshot.id(), snapshot.masterVersion());
        }
    }
}
