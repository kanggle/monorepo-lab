package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.LocationSnapshot;
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
 * Consumes {@code wms.master.location.v1} and upserts {@link LocationSnapshot}
 * rows.
 */
@Component
@Profile("!standalone")
public class MasterLocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterLocationConsumer.class);

    private final MasterEventParser parser;
    private final MasterReadModelWriterPort writer;
    private final EventDedupePort dedupe;
    private final Clock clock;

    public MasterLocationConsumer(MasterEventParser parser,
                                  MasterReadModelWriterPort writer,
                                  EventDedupePort dedupe,
                                  Clock clock) {
        this.parser = parser;
        this.writer = writer;
        this.dedupe = dedupe;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-location:wms.master.location.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        MasterEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "master-location");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("master.location event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(MasterEventEnvelope envelope) {
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
