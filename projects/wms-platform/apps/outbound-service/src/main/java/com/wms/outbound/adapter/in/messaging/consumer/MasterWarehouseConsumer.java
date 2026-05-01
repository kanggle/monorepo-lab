package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
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
 * Consumes {@code wms.master.warehouse.v1} and upserts {@link WarehouseSnapshot}
 * rows.
 */
@Component
@Profile("!standalone")
public class MasterWarehouseConsumer {

    private static final Logger log = LoggerFactory.getLogger(MasterWarehouseConsumer.class);

    private final MasterEventParser parser;
    private final MasterReadModelWriterPort writer;
    private final EventDedupePort dedupe;
    private final Clock clock;

    public MasterWarehouseConsumer(MasterEventParser parser,
                                   MasterReadModelWriterPort writer,
                                   EventDedupePort dedupe,
                                   Clock clock) {
        this.parser = parser;
        this.writer = writer;
        this.dedupe = dedupe;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.master-warehouse:wms.master.warehouse.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        MasterEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "master-warehouse");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("master.warehouse event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(MasterEventEnvelope envelope) {
        JsonNode warehouse = envelope.payload().get("warehouse");
        if (warehouse == null || warehouse.isNull()) {
            throw new IllegalArgumentException(
                    "master.warehouse event missing payload.warehouse: " + envelope.eventType());
        }
        WarehouseSnapshot snapshot = new WarehouseSnapshot(
                UUID.fromString(warehouse.get("id").asText()),
                warehouse.get("warehouseCode").asText(),
                WarehouseSnapshot.Status.valueOf(warehouse.get("status").asText()),
                clock.instant(),
                warehouse.get("version").asLong());
        boolean applied = writer.upsertWarehouse(snapshot);
        if (!applied) {
            log.debug("master.warehouse {} arrived stale (master_version={}); dropped",
                    snapshot.id(), snapshot.masterVersion());
        }
    }
}
