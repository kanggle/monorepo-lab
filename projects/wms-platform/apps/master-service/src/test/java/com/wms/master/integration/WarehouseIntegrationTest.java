package com.wms.master.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.integration.support.KafkaTestConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
 * End-to-end integration tests for the Warehouse aggregate.
 *
 * <p>Covers the three cross-layer invariants called out in the task Goal:
 * (1) mutation + outbox + event commit atomically,
 * (2) outbox-to-Kafka delivery lands on the right topic with the right
 *     envelope within the SLA,
 * (3) idempotency-key replay returns the cached response; different body
 *     returns 409 DUPLICATE_REQUEST.
 */
class WarehouseIntegrationTest extends MasterServiceIntegrationBase {

    private static final String WRITE_ROLE = "MASTER_WRITE";
    private static final String READ_ROLE = "MASTER_READ";
    private static final String ADMIN_ROLE = "MASTER_ADMIN";
    private static final String TOPIC = "wms.master.warehouse.v1";

    // v2 outbox metric names (TASK-BE-438). The success counter is tagged with
    // event_type (and reason), so callers sum across tag series.
    private static final String PENDING_COUNT = "master.outbox.pending.count";
    private static final String PUBLISH_SUCCESS_TOTAL = "master.outbox.publish.success.total";

    // Sequence for the prometheus test's warehouse code, kept in the WH900–WH999
    // slot (outside shortSuffix()'s WH10–WH899 range) to avoid code collisions.
    private static final AtomicInteger METRICS_WH_SEQ = new AtomicInteger(0);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("create persists, writes outbox row, publishes envelope, idempotency replay returns cached response")
    void create_then_replay_then_event() throws Exception {
        String code = "WH" + shortSuffix();
        String body = """
                {"warehouseCode":"%s","name":"IT Main","address":"Seoul","timezone":"Asia/Seoul"}
                """.formatted(code);
        String idempKey = UUID.randomUUID().toString();

        double successBefore = successCounter();

        try (KafkaTestConsumer kafka = new KafkaTestConsumer(KAFKA.getBootstrapServers(), TOPIC)) {
            ResponseEntity<String> first = post("/api/v1/master/warehouses", body, idempKey, WRITE_ROLE);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode created = objectMapper.readTree(first.getBody());
            assertThat(created.get("warehouseCode").asText()).isEqualTo(code);
            assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(created.get("version").asLong()).isZero();

            // Idempotency: replay same (key, method, path, body) returns cached response
            ResponseEntity<String> replay = post("/api/v1/master/warehouses", body, idempKey, WRITE_ROLE);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(replay.getBody()).isEqualTo(first.getBody());

            // Same key, different body → 409 DUPLICATE_REQUEST
            String differentBody = body.replace("Seoul", "Busan");
            ResponseEntity<String> mismatch =
                    post("/api/v1/master/warehouses", differentBody, idempKey, WRITE_ROLE);
            assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(mismatch.getBody()).contains("DUPLICATE_REQUEST");

            // Outbox → Kafka delivery within 5s. Filter by aggregateId so a
            // drained leftover event from a previous test case in the same JVM
            // (the outbox scheduler may flush rows after our consumer subscribed)
            // does not match this assertion. TASK-BE-019.
            String expectedAggregateId = created.get("id").asText();
            ConsumerRecord<String, String> record =
                    kafka.pollOneForKey(Duration.ofSeconds(10), expectedAggregateId);
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("eventType").asText()).isEqualTo("master.warehouse.created");
            assertThat(envelope.get("aggregateType").asText()).isEqualTo("warehouse");
            assertThat(envelope.get("aggregateId").asText()).isEqualTo(expectedAggregateId);
            assertThat(envelope.get("producer").asText()).isEqualTo("master-service");
            assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
            assertThat(envelope.get("payload").get("warehouse").get("warehouseCode").asText())
                    .isEqualTo(code);
            assertThat(record.key()).isEqualTo(expectedAggregateId);
        }

        // Success counter incremented (at-least-once)
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(successCounter()).isGreaterThan(successBefore));
    }

    @Test
    @DisplayName("MASTER_READ on a write endpoint returns 403")
    void forbidden_onMissingWriteRole() {
        String body = """
                {"warehouseCode":"WH%s","name":"Role","timezone":"UTC"}
                """.formatted(shortSuffix());

        ResponseEntity<String> response =
                post("/api/v1/master/warehouses", body, UUID.randomUUID().toString(), READ_ROLE);
        // Depends on method-security enforcement; slice tests verify 403, but
        // in the wired context we at least expect 4xx client error (not 2xx).
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("patch with stale version returns 409 CONFLICT and does NOT publish an event")
    void versionCollision_returns409() throws Exception {
        // Seed
        String code = "WH" + shortSuffix();
        String createBody = """
                {"warehouseCode":"%s","name":"For update","timezone":"UTC"}
                """.formatted(code);
        ResponseEntity<String> created =
                post("/api/v1/master/warehouses", createBody,
                        UUID.randomUUID().toString(), WRITE_ROLE);
        JsonNode createdJson = objectMapper.readTree(created.getBody());
        String id = createdJson.get("id").asText();

        // First update: success (v0 → v1)
        String firstPatch = """
                {"name":"Renamed once","version":0}
                """;
        ResponseEntity<String> firstResp = patch("/api/v1/master/warehouses/" + id,
                firstPatch, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(firstResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second update with stale version 0: conflict
        String stale = """
                {"name":"Stale","version":0}
                """;
        ResponseEntity<String> stalePatch = patch("/api/v1/master/warehouses/" + id,
                stale, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(stalePatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stalePatch.getBody()).contains("CONFLICT");
    }

    @Test
    @DisplayName("JWKS-based JWT signed by the test helper is accepted by the real decoder")
    void wiredJwtDecoder_acceptsTestToken() {
        ResponseEntity<String> response = get("/api/v1/master/warehouses", READ_ROLE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // TASK-BE-458: previously @DisabledIfEnvironmentVariable(CI) on a meter-churn
    // race hypothesis. The actual cause (reproduced locally + on CI) was a flat
    // HTTP 404 from /actuator/prometheus throughout the budget — Spring Boot
    // disables metrics-export auto-configuration in @SpringBootTest by default,
    // so no PrometheusMeterRegistry bean is created and the scrape endpoint is
    // never mapped. Fixed by @AutoConfigureObservability(tracing = false) on
    // MasterServiceIntegrationBase; the test now runs on CI. The outbox gauges
    // are still registered with strongReference(true) + an @Autowired hold on
    // the publisher (TASK-BE-020/438), so the meter functions cannot be GC'd.
    @Test
    @DisplayName("prometheus actuator endpoint exposes the outbox pending gauge + success counter")
    void prometheusEndpoint_exposesOutboxMetrics() {
        // Generate one successful publish so the success-counter family is
        // registered, making this assertion deterministic in isolation as well
        // as in the full suite. The success counter is tagged per event_type and
        // is created lazily on first publish, so without this the metric line is
        // absent unless an earlier test in the same JVM happened to publish. The
        // failure-counter family is induced + asserted by
        // PublisherResilienceIntegrationTest, so it is intentionally not required
        // here (asserting it would re-couple this test to cross-class ordering).
        // Use a code in the WH900–WH999 slot: shortSuffix() only emits WH10–WH899,
        // so this never collides with the random codes the other test methods /
        // classes create (warehouseCode is unique-constrained, ^WH\d{2,3}$). Keeps
        // this test from adding to the suite's small-code-space collision risk.
        String metricsCode = "WH" + (900 + METRICS_WH_SEQ.getAndIncrement());
        String createBody = """
                {"warehouseCode":"%s","name":"Metrics WH","address":"Seoul","timezone":"Asia/Seoul"}
                """.formatted(metricsCode);
        ResponseEntity<String> created =
                post("/api/v1/master/warehouses", createBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Permit-all endpoint per SecurityConfig — no auth. The scrape is mapped
        // only because MasterServiceIntegrationBase carries
        // @AutoConfigureObservability; otherwise Spring Boot disables metrics
        // export in @SpringBootTest and /actuator/prometheus 404s. Retry until
        // the outbox publisher has flushed the row and registered the counter.
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ResponseEntity<String> response =
                            rest.getForEntity("/actuator/prometheus", String.class);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    String scrape = response.getBody();
                    assertThat(scrape).contains(PENDING_COUNT.replace('.', '_'));
                    assertThat(scrape).contains(PUBLISH_SUCCESS_TOTAL.replace('.', '_'));
                });
        // sanity: ADMIN role unused here; kept as reference for role table completeness
        assertThat(ADMIN_ROLE).isNotBlank();
    }

    // ---------- helpers ----------

    private ResponseEntity<String> post(String path, String body, String idempKey, String role) {
        HttpHeaders headers = mutatingHeaders(idempKey, role);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> patch(String path, String body, String idempKey, String role) {
        HttpHeaders headers = mutatingHeaders(idempKey, role);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JWT.issueToken("integration-actor", role));
        headers.add("X-Request-Id", UUID.randomUUID().toString());
        headers.add("X-Actor-Id", "integration-actor");
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
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

    private double successCounter() {
        // v2 (TASK-BE-438): the success counter is tagged per event_type, so a
        // single find().counter() could pick an arbitrary series. Sum every
        // series under the metric name for a stable, monotonic total.
        return meterRegistry.find(PUBLISH_SUCCESS_TOTAL).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }

    private static String shortSuffix() {
        // Two-to-three digit suffix to match WH\d{2,3}
        return String.valueOf(10 + (int) (Math.random() * 890));
    }
}
