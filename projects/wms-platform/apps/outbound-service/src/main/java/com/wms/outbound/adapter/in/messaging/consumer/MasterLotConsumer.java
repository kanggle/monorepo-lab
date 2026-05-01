package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.LotSnapshot;
import java.time.Clock;
import java.time.LocalDate;
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
 * Consumes {@code wms.master.lot.v1}. Lots flow through {@code ACTIVE},
 * {@code INACTIVE}, and {@code EXPIRED}; the consumer mirrors whichever
 * status the inbound payload carries.
 */
@Component
@Profile("!standalone")
public class MasterLotConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterLotConsumer.class);

    private final MasterEventParser parser;
    private final MasterReadModelWriterPort writer;
    private final EventDedupePort dedupe;
    private final Clock clock;

    public MasterLotConsumer(MasterEventParser parser,
                             MasterReadModelWriterPort writer,
                             EventDedupePort dedupe,
                             Clock clock) {
        this.parser = parser;
        this.writer = writer;
        this.dedupe = dedupe;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-lot:wms.master.lot.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        MasterEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "master-lot");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("master.lot event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(MasterEventEnvelope envelope) {
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
