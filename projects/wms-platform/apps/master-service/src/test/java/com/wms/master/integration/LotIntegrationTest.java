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
 * Lot aggregate end-to-end integration test (TASK-BE-527). Mirrors
 * {@link WarehouseIntegrationTest} / {@link LocationIntegrationTest} for the
 * full-stack invariants: mutation + outbox + Kafka delivery, idempotency-key
 * replay/conflict, and the MASTER_WRITE/MASTER_ADMIN authority split
 * (create/update = WRITE|ADMIN; deactivate/reactivate = ADMIN only). The
 * expiry batch path ({@code LotExpirationBatchProcessor}) is out of scope —
 * already exercised by {@code LotServiceTest}; this IT covers only the
 * write/lifecycle + idempotency surface reachable via nested-under-SKU HTTP
 * routes.
 */
class LotIntegrationTest extends MasterServiceIntegrationBase {

    private static final String WRITE_ROLE = "MASTER_WRITE";
    private static final String READ_ROLE = "MASTER_READ";
    private static final String ADMIN_ROLE = "MASTER_ADMIN";
    private static final String TOPIC = "wms.master.lot.v1";
    private static final String LOTS_PATH = "/api/v1/master/lots";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("create under a LOT-tracked ACTIVE SKU persists, writes outbox row, publishes "
            + "envelope, idempotency replay returns cached response, mismatched replay returns "
            + "409 DUPLICATE_REQUEST")
    void create_then_replay_then_conflict_then_event() throws Exception {
        String skuId = seedLotTrackedSku();
        String lotNo = "LOT" + TestCodes.uniqueSuffix();
        String createPath = "/api/v1/master/skus/" + skuId + "/lots";
        String body = """
                {"lotNo":"%s","manufacturedDate":"2026-01-01","expiryDate":"2026-12-31"}
                """.formatted(lotNo);
        String idempKey = UUID.randomUUID().toString();

        try (KafkaTestConsumer kafka = new KafkaTestConsumer(KAFKA.getBootstrapServers(), TOPIC)) {
            ResponseEntity<String> first = post(createPath, body, idempKey, WRITE_ROLE);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode created = objectMapper.readTree(first.getBody());
            assertThat(created.get("lotNo").asText()).isEqualTo(lotNo);
            assertThat(created.get("skuId").asText()).isEqualTo(skuId);
            assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(created.get("version").asLong()).isZero();

            // Idempotency: replay same (key, method, path, body) returns cached response
            ResponseEntity<String> replay = post(createPath, body, idempKey, WRITE_ROLE);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(replay.getBody()).isEqualTo(first.getBody());

            // Same key, different body -> 409 DUPLICATE_REQUEST
            String differentBody = body.replace("2026-12-31", "2027-06-30");
            ResponseEntity<String> mismatch = post(createPath, differentBody, idempKey, WRITE_ROLE);
            assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(mismatch.getBody()).contains("DUPLICATE_REQUEST");

            String expectedAggregateId = created.get("id").asText();
            ConsumerRecord<String, String> record =
                    kafka.pollOneForKey(Duration.ofSeconds(10), expectedAggregateId);
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("eventType").asText()).isEqualTo("master.lot.created");
            assertThat(envelope.get("aggregateType").asText()).isEqualTo("lot");
            assertThat(envelope.get("aggregateId").asText()).isEqualTo(expectedAggregateId);
            assertThat(envelope.get("producer").asText()).isEqualTo("master-service");
            assertThat(envelope.get("payload").get("lot").get("lotNo").asText())
                    .isEqualTo(lotNo);
            assertThat(record.key()).isEqualTo(expectedAggregateId);
        }
    }

    @Test
    @DisplayName("MASTER_READ on a write endpoint returns 403 FORBIDDEN")
    void forbidden_onMissingWriteRole() throws Exception {
        String skuId = seedLotTrackedSku();
        String body = """
                {"lotNo":"LOT%s","manufacturedDate":"2026-01-01","expiryDate":"2026-12-31"}
                """.formatted(TestCodes.uniqueSuffix());

        ResponseEntity<String> response = post("/api/v1/master/skus/" + skuId + "/lots",
                body, UUID.randomUUID().toString(), READ_ROLE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FORBIDDEN");
    }

    @Test
    @DisplayName("MASTER_WRITE caller is rejected (403) on the MASTER_ADMIN-only deactivate op")
    void adminOnlyOp_rejectedForWriteRole() throws Exception {
        String lotId = seedLot();

        String deactivateBody = """
                {"version":0,"reason":"Not admin"}
                """;
        ResponseEntity<String> response = post(LOTS_PATH + "/" + lotId + "/deactivate",
                deactivateBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FORBIDDEN");
    }

    @Test
    @DisplayName("PATCH updates mutable fields (expiryDate) under MASTER_WRITE")
    void update_appliesPatch() throws Exception {
        String lotId = seedLot();

        String patchBody = """
                {"expiryDate":"2027-01-31","version":0}
                """;
        ResponseEntity<String> patched = patch(LOTS_PATH + "/" + lotId,
                patchBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = objectMapper.readTree(patched.getBody());
        assertThat(node.get("expiryDate").asText()).isEqualTo("2027-01-31");
        assertThat(node.get("version").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("MASTER_ADMIN deactivate then reactivate cycles ACTIVE -> INACTIVE -> ACTIVE")
    void deactivateReactivate_lifecycle() throws Exception {
        String lotId = seedLot();

        String deactivateBody = """
                {"version":0,"reason":"Quality hold"}
                """;
        ResponseEntity<String> deactivated = post(LOTS_PATH + "/" + lotId + "/deactivate",
                deactivateBody, UUID.randomUUID().toString(), ADMIN_ROLE);
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode deactivatedNode = objectMapper.readTree(deactivated.getBody());
        assertThat(deactivatedNode.get("status").asText()).isEqualTo("INACTIVE");
        long versionAfterDeactivate = deactivatedNode.get("version").asLong();

        String reactivateBody = """
                {"version":%d}
                """.formatted(versionAfterDeactivate);
        ResponseEntity<String> reactivated = post(LOTS_PATH + "/" + lotId + "/reactivate",
                reactivateBody, UUID.randomUUID().toString(), ADMIN_ROLE);
        assertThat(reactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode reactivatedNode = objectMapper.readTree(reactivated.getBody());
        assertThat(reactivatedNode.get("status").asText()).isEqualTo("ACTIVE");
    }

    // ---------- helpers ----------

    private String seedLotTrackedSku() throws Exception {
        String code = "SKU" + TestCodes.uniqueSuffix();
        String body = """
                {"skuCode":"%s","name":"Lot Parent SKU","baseUom":"EA","trackingType":"LOT"}
                """.formatted(code);
        ResponseEntity<String> created = post("/api/v1/master/skus",
                body, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(created.getBody()).get("id").asText();
    }

    private String seedLot() throws Exception {
        String skuId = seedLotTrackedSku();
        String lotNo = "LOT" + TestCodes.uniqueSuffix();
        String body = """
                {"lotNo":"%s","manufacturedDate":"2026-01-01","expiryDate":"2026-12-31"}
                """.formatted(lotNo);
        ResponseEntity<String> created = post("/api/v1/master/skus/" + skuId + "/lots",
                body, UUID.randomUUID().toString(), WRITE_ROLE);
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
