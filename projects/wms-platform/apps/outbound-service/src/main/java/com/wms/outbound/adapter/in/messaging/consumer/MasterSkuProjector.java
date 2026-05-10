package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects {@code wms.master.sku.v1} events into the local
 * {@link SkuSnapshot} read-model.
 */
@Component
class MasterSkuProjector implements MasterEventProjector {

    private static final Logger log = LoggerFactory.getLogger(MasterSkuProjector.class);

    private final MasterReadModelWriterPort writer;
    private final Clock clock;

    MasterSkuProjector(MasterReadModelWriterPort writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void apply(EventEnvelope envelope) {
        JsonNode sku = envelope.payload().get("sku");
        if (sku == null || sku.isNull()) {
            throw new IllegalArgumentException(
                    "master.sku event missing payload.sku: " + envelope.eventType());
        }
        SkuSnapshot snapshot = new SkuSnapshot(
                UUID.fromString(sku.get("id").asText()),
                sku.get("skuCode").asText(),
                SkuSnapshot.TrackingType.valueOf(sku.get("trackingType").asText()),
                SkuSnapshot.Status.valueOf(sku.get("status").asText()),
                clock.instant(),
                sku.get("version").asLong());
        boolean applied = writer.upsertSku(snapshot);
        if (!applied) {
            log.debug("master.sku {} arrived stale (master_version={}); dropped",
                    snapshot.id(), snapshot.masterVersion());
        }
    }
}
