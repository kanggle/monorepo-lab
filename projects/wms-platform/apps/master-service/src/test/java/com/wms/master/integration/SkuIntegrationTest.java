package com.wms.master.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.integration.support.KafkaTestConsumer;
import com.wms.master.integration.support.TestCodes;
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
 * Sku aggregate end-to-end integration test (TASK-BE-527). Mirrors
 * {@link WarehouseIntegrationTest} for the full-stack invariants: mutation +
 * outbox + Kafka delivery, idempotency-key replay/conflict, and the
 * MASTER_WRITE/MASTER_ADMIN authority split (create/update = WRITE|ADMIN;
 * deactivate/reactivate = ADMIN only).
 */
class SkuIntegrationTest extends MasterServiceIntegrationBase {

    private static final String WRITE_ROLE = "MASTER_WRITE";
    private static final String READ_ROLE = "MASTER_READ";
    private static final String ADMIN_ROLE = "MASTER_ADMIN";
    private static final String TOPIC = "wms.master.sku.v1";
    private static final String BASE_PATH = "/api/v1/master/skus";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("create persists, writes outbox row, publishes envelope, idempotency replay "
            + "returns cached response, mismatched replay returns 409 DUPLICATE_REQUEST")
    void create_then_replay_then_conflict_then_event() throws Exception {
        String code = "SKU" + TestCodes.uniqueSuffix();
        String body = """
                {"skuCode":"%s","name":"IT Widget","baseUom":"EA","trackingType":"NONE"}
                """.formatted(code);
        String idempKey = UUID.randomUUID().toString();

        try (KafkaTestConsumer kafka = new KafkaTestConsumer(KAFKA.getBootstrapServers(), TOPIC)) {
            ResponseEntity<String> first = post(BASE_PATH, body, idempKey, WRITE_ROLE);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode created = objectMapper.readTree(first.getBody());
            assertThat(created.get("skuCode").asText()).isEqualTo(code);
            assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(created.get("version").asLong()).isZero();

            // Idempotency: replay same (key, method, path, body) returns cached response
            ResponseEntity<String> replay = post(BASE_PATH, body, idempKey, WRITE_ROLE);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(replay.getBody()).isEqualTo(first.getBody());

            // Same key, different body -> 409 DUPLICATE_REQUEST
            String differentBody = body.replace("IT Widget", "IT Gadget");
            ResponseEntity<String> mismatch = post(BASE_PATH, differentBody, idempKey, WRITE_ROLE);
            assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(mismatch.getBody()).contains("DUPLICATE_REQUEST");

            String expectedAggregateId = created.get("id").asText();
            ConsumerRecord<String, String> record =
                    kafka.pollOneForKey(Duration.ofSeconds(10), expectedAggregateId);
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("eventType").asText()).isEqualTo("master.sku.created");
            assertThat(envelope.get("aggregateType").asText()).isEqualTo("sku");
            assertThat(envelope.get("aggregateId").asText()).isEqualTo(expectedAggregateId);
            assertThat(envelope.get("producer").asText()).isEqualTo("master-service");
            assertThat(envelope.get("payload").get("sku").get("skuCode").asText())
                    .isEqualTo(code);
            assertThat(record.key()).isEqualTo(expectedAggregateId);
        }
    }

    @Test
    @DisplayName("MASTER_READ on a write endpoint returns 403 FORBIDDEN")
    void forbidden_onMissingWriteRole() {
        String body = """
                {"skuCode":"SKU%s","name":"Role Test","baseUom":"EA","trackingType":"NONE"}
                """.formatted(TestCodes.uniqueSuffix());

        ResponseEntity<String> response =
                post(BASE_PATH, body, UUID.randomUUID().toString(), READ_ROLE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FORBIDDEN");
    }

    @Test
    @DisplayName("MASTER_WRITE caller is rejected (403) on the MASTER_ADMIN-only deactivate op")
    void adminOnlyOp_rejectedForWriteRole() throws Exception {
        String skuId = seedSku();

        String deactivateBody = """
                {"version":0,"reason":"Not admin"}
                """;
        ResponseEntity<String> response = post(BASE_PATH + "/" + skuId + "/deactivate",
                deactivateBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FORBIDDEN");
    }

    @Test
    @DisplayName("PATCH updates mutable fields under MASTER_WRITE")
    void update_appliesPatch() throws Exception {
        String skuId = seedSku();

        String patchBody = """
                {"name":"Renamed Widget","version":0}
                """;
        ResponseEntity<String> patched = patch(BASE_PATH + "/" + skuId,
                patchBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = objectMapper.readTree(patched.getBody());
        assertThat(node.get("name").asText()).isEqualTo("Renamed Widget");
        assertThat(node.get("version").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("MASTER_ADMIN deactivate then reactivate cycles ACTIVE -> INACTIVE -> ACTIVE")
    void deactivateReactivate_lifecycle() throws Exception {
        String skuId = seedSku();

        String deactivateBody = """
                {"version":0,"reason":"Discontinued"}
                """;
        ResponseEntity<String> deactivated = post(BASE_PATH + "/" + skuId + "/deactivate",
                deactivateBody, UUID.randomUUID().toString(), ADMIN_ROLE);
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode deactivatedNode = objectMapper.readTree(deactivated.getBody());
        assertThat(deactivatedNode.get("status").asText()).isEqualTo("INACTIVE");
        long versionAfterDeactivate = deactivatedNode.get("version").asLong();

        String reactivateBody = """
                {"version":%d}
                """.formatted(versionAfterDeactivate);
        ResponseEntity<String> reactivated = post(BASE_PATH + "/" + skuId + "/reactivate",
                reactivateBody, UUID.randomUUID().toString(), ADMIN_ROLE);
        assertThat(reactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode reactivatedNode = objectMapper.readTree(reactivated.getBody());
        assertThat(reactivatedNode.get("status").asText()).isEqualTo("ACTIVE");
    }

    // ---------- helpers ----------

    private String seedSku() throws Exception {
        String code = "SKU" + TestCodes.uniqueSuffix();
        String body = """
                {"skuCode":"%s","name":"Seed Widget","baseUom":"EA","trackingType":"NONE"}
                """.formatted(code);
        ResponseEntity<String> created = post(BASE_PATH, body, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(created.getBody()).get("id").asText();
    }

    private ResponseEntity<String> post(String path, String body, String idempKey, String role) {
        HttpHeaders headers = mutatingHeaders(idempKey, role);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> patch(String path, String body, String idempKey, String role) {
        HttpHeaders headers = mutatingHeaders(idempKey, role);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders mutatingHeaders(String idempKey, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JWT.issueToken("integration-actor", role));
        headers.add("Idempotency-Key", idempKey);
        headers.add("X-Request-Id", UUID.randomUUID().toString());
        headers.add("X-Actor-Id", "integration-actor");
        return headers;
    }
}
