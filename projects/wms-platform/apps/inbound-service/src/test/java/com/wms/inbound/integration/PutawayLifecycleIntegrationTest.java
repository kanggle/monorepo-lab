package com.wms.inbound.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.application.command.CloseAsnCommand;
import com.wms.inbound.application.command.ConfirmPutawayLineCommand;
import com.wms.inbound.application.command.InstructPutawayCommand;
import com.wms.inbound.application.command.RecordInspectionCommand;
import com.wms.inbound.application.command.StartInspectionCommand;
import com.wms.inbound.application.port.in.CloseAsnUseCase;
import com.wms.inbound.application.port.in.ConfirmPutawayLineUseCase;
import com.wms.inbound.application.port.in.InstructPutawayUseCase;
import com.wms.inbound.application.port.in.RecordInspectionUseCase;
import com.wms.inbound.application.port.in.StartInspectionUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.result.CloseAsnResult;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawayInstructionResult;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Golden-path integration test for Phase 3..5 of the inbound flow.
 *
 * <p>Seeds master snapshots + ASN/Inspection rows directly via JDBC; then drives
 * the lifecycle through the application-port use cases and asserts that the
 * three Kafka topics ({@code wms.inbound.putaway.instructed.v1},
 * {@code wms.inbound.putaway.completed.v1}, {@code wms.inbound.asn.closed.v1})
 * receive correctly-shaped envelopes.
 */
class PutawayLifecycleIntegrationTest extends InboundServiceIntegrationBase {

    private static final String TOPIC_INSTRUCTED = "wms.inbound.putaway.instructed.v1";
    private static final String TOPIC_COMPLETED = "wms.inbound.putaway.completed.v1";
    private static final String TOPIC_CLOSED = "wms.inbound.asn.closed.v1";

    @Autowired StartInspectionUseCase startInspection;
    @Autowired RecordInspectionUseCase recordInspection;
    @Autowired InstructPutawayUseCase instructPutaway;
    @Autowired ConfirmPutawayLineUseCase confirmPutawayLine;
    @Autowired CloseAsnUseCase closeAsn;
    @Autowired AsnPersistencePort asnPersistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired Clock clock;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM putaway_confirmation");
        jdbc.update("DELETE FROM putaway_line");
        jdbc.update("DELETE FROM putaway_instruction");
        jdbc.update("DELETE FROM inspection_discrepancy");
        jdbc.update("DELETE FROM inspection_line");
        jdbc.update("DELETE FROM inspection");
        jdbc.update("DELETE FROM asn_line");
        jdbc.update("DELETE FROM asn");
        jdbc.update("DELETE FROM inbound_outbox");
        jdbc.update("DELETE FROM location_snapshot");
        jdbc.update("DELETE FROM sku_snapshot");
        jdbc.update("DELETE FROM warehouse_snapshot");
        jdbc.update("DELETE FROM zone_snapshot");
    }

    @Test
    @DisplayName("golden path: INSPECTED → instruct → confirm last → completed event → close → closed event")
    void putawayLifecycle_publishesAllEvents() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();

        seedMasterSnapshots(warehouseId, zoneId, locationId, skuId);

        UUID asnId = UUID.randomUUID();
        UUID asnLineId = UUID.randomUUID();
        Asn asn = createdAsn(asnId, asnLineId, warehouseId, skuId);
        asnPersistence.save(asn);

        Set<String> roles = Set.of("ROLE_INBOUND_WRITE", "ROLE_INBOUND_ADMIN");

        // Phase 1: progress through inspection.
        startInspection.start(new StartInspectionCommand(asnId, "user-1"));
        recordInspection.record(new RecordInspectionCommand(
                asnId, null,
                List.of(new RecordInspectionCommand.Line(asnLineId, null, null, 100, 0, 0)),
                "user-1"));

        // Phase 3: putaway instruction.
        PutawayInstructionResult instructionResult = instructPutaway.instruct(
                new InstructPutawayCommand(asnId,
                        List.of(new InstructPutawayCommand.Line(asnLineId, locationId, 100)),
                        0L, "user-1", roles));
        UUID instructionId = instructionResult.putawayInstructionId();
        UUID putawayLineId = instructionResult.lines().get(0).putawayLineId();

        // Phase 4: confirm last (only) line.
        PutawayConfirmationResult confirmResult = confirmPutawayLine.confirm(
                new ConfirmPutawayLineCommand(instructionId, putawayLineId,
                        locationId, 100, "user-1", roles));
        assertThat(confirmResult.instruction().status()).isEqualTo("COMPLETED");
        assertThat(confirmResult.asn().status()).isEqualTo("PUTAWAY_DONE");

        // Phase 5: close ASN.
        CloseAsnResult closeResult = closeAsn.close(
                new CloseAsnCommand(asnId, 0L, "user-1", roles));
        assertThat(closeResult.status()).isEqualTo("CLOSED");
        assertThat(closeResult.summary().putawayConfirmedTotal()).isEqualTo(100);

        // Wait for outbox publisher to push the three events to Kafka.
        try (KafkaConsumer<String, String> consumer = newConsumer(
                List.of(TOPIC_INSTRUCTED, TOPIC_COMPLETED, TOPIC_CLOSED))) {
            JsonNode instructed = pollEventOnTopic(consumer, TOPIC_INSTRUCTED);
            assertThat(instructed.get("eventType").asText()).isEqualTo("inbound.putaway.instructed");
            assertThat(instructed.get("payload").get("asnId").asText()).isEqualTo(asnId.toString());

            JsonNode completed = pollEventOnTopic(consumer, TOPIC_COMPLETED);
            assertThat(completed.get("eventType").asText()).isEqualTo("inbound.putaway.completed");
            assertThat(completed.get("payload").get("asnId").asText()).isEqualTo(asnId.toString());
            JsonNode lines = completed.get("payload").get("lines");
            assertThat(lines.size()).isEqualTo(1);
            assertThat(lines.get(0).get("qtyReceived").asInt()).isEqualTo(100);

            JsonNode closed = pollEventOnTopic(consumer, TOPIC_CLOSED);
            assertThat(closed.get("eventType").asText()).isEqualTo("inbound.asn.closed");
            assertThat(closed.get("payload").get("summary").get("putawayConfirmedTotal").asInt()).isEqualTo(100);
        }
    }

    private Asn createdAsn(UUID asnId, UUID asnLineId, UUID warehouseId, UUID skuId) {
        Instant now = clock.instant();
        AsnLine line = new AsnLine(asnLineId, asnId, 1, skuId, null, 100);
        return new Asn(asnId, "ASN-IT-" + asnId.toString().substring(0, 8),
                AsnSource.MANUAL, UUID.randomUUID(), warehouseId,
                LocalDate.of(2026, 5, 1), null,
                AsnStatus.CREATED, 0L,
                now, "test-seed", now, "test-seed",
                List.of(line));
    }

    private void seedMasterSnapshots(UUID warehouseId, UUID zoneId, UUID locationId, UUID skuId) {
        jdbc.update("""
                INSERT INTO warehouse_snapshot
                (id, warehouse_code, status, cached_at, master_version)
                VALUES (?, ?, 'ACTIVE', now(), 0)
                """, warehouseId, "WH-IT");
        jdbc.update("""
                INSERT INTO zone_snapshot
                (id, warehouse_id, zone_code, zone_type, status, cached_at, master_version)
                VALUES (?, ?, ?, 'AMBIENT', 'ACTIVE', now(), 0)
                """, zoneId, warehouseId, "Z-IT");
        jdbc.update("""
                INSERT INTO location_snapshot
                (id, location_code, warehouse_id, zone_id, location_type, status, cached_at, master_version)
                VALUES (?, ?, ?, ?, 'STORAGE', 'ACTIVE', now(), 0)
                """, locationId, "WH-IT-A-01", warehouseId, zoneId);
        jdbc.update("""
                INSERT INTO sku_snapshot
                (id, sku_code, tracking_type, status, cached_at, master_version)
                VALUES (?, ?, 'NONE', 'ACTIVE', now(), 0)
                """, skuId, "SKU-IT");
    }

    private KafkaConsumer<String, String> newConsumer(List<String> topics) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "lifecycle-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(topics);
        return consumer;
    }

    private JsonNode pollEventOnTopic(KafkaConsumer<String, String> consumer, String topic) {
        return await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> r : records) {
                        if (topic.equals(r.topic())) {
                            return objectMapper.readTree(r.value());
                        }
                    }
                    return null;
                }, java.util.Objects::nonNull);
    }
}
