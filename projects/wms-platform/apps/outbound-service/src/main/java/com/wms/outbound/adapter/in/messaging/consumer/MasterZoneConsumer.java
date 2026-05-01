package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.ZoneSnapshot;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.master.zone.v1} and upserts {@link ZoneSnapshot} rows.
 */
@Component
@Profile("!standalone")
public class MasterZoneConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterZoneConsumer.class);

    private final MasterEventParser parser;
    private final MasterReadModelWriterPort writer;
    private final EventDedupePort dedupe;
    private final Clock clock;

    public MasterZoneConsumer(MasterEventParser parser,
                              MasterReadModelWriterPort writer,
                              EventDedupePort dedupe,
                              Clock clock) {
        this.parser = parser;
        this.writer = writer;
        this.dedupe = dedupe;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-zone:wms.master.zone.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        MasterEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "master-zone");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("master.zone event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(MasterEventEnvelope envelope) {
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
