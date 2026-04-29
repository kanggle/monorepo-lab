package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
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
 * Consumes {@code wms.master.partner.v1} and upserts {@link PartnerSnapshot}
 * rows. Order customer validation reads from this snapshot via
 * {@code MasterReadModelPort.findPartner(id)}.
 */
@Component
@Profile("!standalone")
public class MasterPartnerConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterPartnerConsumer.class);

    private final MasterEventParser parser;
    private final MasterReadModelWriterPort writer;
    private final EventDedupePort dedupe;
    private final Clock clock;

    public MasterPartnerConsumer(MasterEventParser parser,
                                 MasterReadModelWriterPort writer,
                                 EventDedupePort dedupe,
                                 Clock clock) {
        this.parser = parser;
        this.writer = writer;
        this.dedupe = dedupe;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-partner:wms.master.partner.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        MasterEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "master-partner");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("master.partner event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(MasterEventEnvelope envelope) {
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
