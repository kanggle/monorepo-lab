package com.wms.master.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.integration.support.KafkaTestConsumer;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Zone aggregate integration test.
 *
 * <p>Covers the zone-specific end-to-end invariants:
 * <ul>
 *   <li>Parent-warehouse-active guard (AC: zone create on INACTIVE warehouse
 *       returns 409 STATE_TRANSITION_INVALID)</li>
 *   <li>Outbox → {@code wms.master.zone.v1} delivery with the correct envelope</li>
 *   <li>Idempotency replay on a nested route</li>
 * </ul>
 */
class ZoneIntegrationTest extends MasterServiceIntegrationBase {

    private static final String WRITE_ROLE = "MASTER_WRITE";
    private static final String ADMIN_ROLE = "MASTER_ADMIN";
    private static final String TOPIC = "wms.master.zone.v1";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("zone create under ACTIVE warehouse succeeds, publishes master.zone.created")
    void createZone_publishesEvent() throws Exception {
        String warehouseId = seedWarehouse();

        String zoneCode = "Z-" + shortSuffix();
        String zoneBody = """
                {"zoneCode":"%s","name":"Ambient","zoneType":"AMBIENT"}
                """.formatted(zoneCode);
        String idempKey = UUID.randomUUID().toString();

        try (KafkaTestConsumer kafka = new KafkaTestConsumer(KAFKA.getBootstrapServers(), TOPIC)) {
            ResponseEntity<String> created =
                    post("/api/v1/master/warehouses/" + warehouseId + "/zones",
                            zoneBody, idempKey, WRITE_ROLE);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode node = objectMapper.readTree(created.getBody());
            assertThat(node.get("zoneCode").asText()).isEqualTo(zoneCode);
            assertThat(node.get("warehouseId").asText()).isEqualTo(warehouseId);

            // Replay returns cached
            ResponseEntity<String> replay =
                    post("/api/v1/master/warehouses/" + warehouseId + "/zones",
                            zoneBody, idempKey, WRITE_ROLE);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(replay.getBody()).isEqualTo(created.getBody());

            ConsumerRecord<String, String> record = kafka.pollOne(Duration.ofSeconds(10));
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("eventType").asText()).isEqualTo("master.zone.created");
            assertThat(envelope.get("aggregateType").asText()).isEqualTo("zone");
            assertThat(envelope.get("aggregateId").asText()).isEqualTo(node.get("id").asText());
            assertThat(envelope.get("payload").get("zone").get("warehouseId").asText())
                    .isEqualTo(warehouseId);
        }
    }

    @Test
    @DisplayName("zone create under INACTIVE warehouse returns 409 STATE_TRANSITION_INVALID")
    void createZone_whenWarehouseInactive_blocks() throws Exception {
        String warehouseId = seedWarehouse();

        // Deactivate warehouse
        String deactivate = """
                {"version":0,"reason":"Closing"}
                """;
        ResponseEntity<String> deactivateResp = post(
                "/api/v1/master/warehouses/" + warehouseId + "/deactivate",
                deactivate, UUID.randomUUID().toString(), ADMIN_ROLE);
        assertThat(deactivateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String zoneBody = """
                {"zoneCode":"Z-%s","name":"Blocked","zoneType":"AMBIENT"}
                """.formatted(shortSuffix());
        ResponseEntity<String> attempt = post(
                "/api/v1/master/warehouses/" + warehouseId + "/zones",
                zoneBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(attempt.getBody()).contains("STATE_TRANSITION_INVALID");
    }

    // ---------- helpers ----------

    private String seedWarehouse() throws Exception {
        String code = "WH" + shortSuffix();
        String body = """
                {"warehouseCode":"%s","name":"Zone parent","timezone":"UTC"}
                """.formatted(code);
        ResponseEntity<String> created = post("/api/v1/master/warehouses",
                body, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(created.getBody()).get("id").asText();
    }

    private ResponseEntity<String> post(String path, String body, String idempKey, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JWT.issueToken("integration-actor", role));
        headers.add("Idempotency-Key", idempKey);
        headers.add("X-Request-Id", UUID.randomUUID().toString());
        headers.add("X-Actor-Id", "integration-actor");
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private static String shortSuffix() {
        return String.valueOf(10 + (int) (Math.random() * 890));
    }
}
