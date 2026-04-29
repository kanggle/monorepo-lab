package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
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
 * Consumes {@code wms.master.sku.v1} and upserts {@link SkuSnapshot} rows.
 */
@Component
@Profile("!standalone")
public class MasterSkuConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterSkuConsumer.class);

    private final MasterEventParser parser;
    private final MasterReadModelWriterPort writer;
    private final EventDedupePort dedupe;
    private final Clock clock;

    public MasterSkuConsumer(MasterEventParser parser,
                             MasterReadModelWriterPort writer,
                             EventDedupePort dedupe,
                             Clock clock) {
        this.parser = parser;
        this.writer = writer;
        this.dedupe = dedupe;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-sku:wms.master.sku.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        MasterEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "master-sku");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("master.sku event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(MasterEventEnvelope envelope) {
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
