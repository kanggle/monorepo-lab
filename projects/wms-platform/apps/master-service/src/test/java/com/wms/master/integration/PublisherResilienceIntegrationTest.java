package com.wms.master.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.integration.support.KafkaTestConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the outbox is crash-safe against a Kafka outage:
 *
 * <ol>
 *   <li>Pause Kafka (via Testcontainers container pause)</li>
 *   <li>Issue mutations — publisher cannot deliver, rows accumulate as PENDING</li>
 *   <li>Confirm DB rows remain visible (no data loss) and failure counter grows</li>
 *   <li>Resume Kafka, assert rows drain within the retry window</li>
 * </ol>
 *
 * <p>Runs under {@code @Tag("integration")} only, because the whole point is
 * real broker behavior.
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)} (TASK-BE-458): pausing/unpausing the
 * shared Kafka container forces this context's Kafka clients into a reconnect
 * storm, during which micrometer-kafka continuously re-attaches its client
 * meters to the restarted broker. That meter churn races the
 * {@code /actuator/prometheus} scrape-body composition, so any later test class
 * reusing this cached context (notably
 * {@link WarehouseIntegrationTest#prometheusEndpoint_exposesOutboxMetrics()})
 * intermittently scrapes a body missing the outbox meter families. This is the
 * ONLY context-polluting class in the suite, so discarding its context after the
 * class guarantees every subsequent class boots a fresh context whose clients
 * connect once, cleanly, to the now-stable broker — no reconnect churn, no
 * scrape race.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublisherResilienceIntegrationTest extends MasterServiceIntegrationBase {

    private static final String WRITE_ROLE = "MASTER_WRITE";
    private static final String TOPIC = "wms.master.warehouse.v1";

    // v2 outbox metric names (TASK-BE-438).
    private static final String PENDING_COUNT = "master.outbox.pending.count";
    private static final String PUBLISH_FAILURE_TOTAL = "master.outbox.publish.failure.total";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Kafka pause → outbox accumulates, counter rises; resume → rows drain")
    void outboxSurvivesKafkaOutage() throws Exception {
        double failuresBefore = failureCounter();

        // Subscribe BEFORE the broker is paused so the consumer's partition
        // assignment lands while the cluster is healthy, and its committed
        // offset is {@code latest} at the moment we pause. Any record that is
        // published after unpause is then guaranteed to be delivered to us.
        try (KafkaTestConsumer kafka = new KafkaTestConsumer(KAFKA.getBootstrapServers(), TOPIC)) {

            String createdId;

            // Step 1: pause Kafka (SIGSTOP inside container)
            KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
            try {
                // Step 2: issue a mutation — commits to DB, outbox row written,
                // publisher retries against a paused broker until its bounded
                // `delivery.timeout.ms` (see application-integration.yml) fires
                // and surfaces the failure to onKafkaSendFailure.
                String code = "WH" + shortSuffix();
                String body = """
                        {"warehouseCode":"%s","name":"Paused","timezone":"UTC"}
                        """.formatted(code);
                ResponseEntity<String> created = post("/api/v1/master/warehouses",
                        body, UUID.randomUUID().toString(), WRITE_ROLE);
                assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                createdId = objectMapper.readTree(created.getBody()).get("id").asText();

                // Step 3: the DB-resident outbox row persists (no data loss).
                // We can't enter transactional context from here easily; instead we
                // poll the pending count from the gauge which queries the DB.
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(pendingGauge()).isGreaterThanOrEqualTo(1.0d));

                // Failure counter must eventually increment (publisher tried and failed).
                // delivery.timeout.ms=10s in the integration profile makes this ~12 s
                // worst-case; 25 s gives enough headroom for CI scheduling jitter.
                await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                        assertThat(failureCounter()).isGreaterThan(failuresBefore));
            } finally {
                // Step 4: resume Kafka
                KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
            }

            // Step 5: outbox drains — pending count returns to zero and the event
            // lands on Kafka within the retry window.
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(pendingGauge()).isEqualTo(0.0d));
            // The warehouse we created during the outage lands on Kafka after
            // resume. Filter by aggregateId (record key) so a leftover event
            // from a previous test case cannot spuriously satisfy this check.
            assertThat(kafka.pollOneForKey(Duration.ofSeconds(15), createdId)).isNotNull();
        }
    }

    // ---------- helpers ----------

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

    @Transactional
    double pendingGauge() {
        io.micrometer.core.instrument.Gauge g =
                meterRegistry.find(PENDING_COUNT).gauge();
        return g == null ? -1.0 : g.value();
    }

    private double failureCounter() {
        // v2 (TASK-BE-438): the failure counter is tagged (event_type, reason),
        // so sum across all series under the metric name.
        return meterRegistry.find(PUBLISH_FAILURE_TOTAL).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }

    private static String shortSuffix() {
        return String.valueOf(10 + (int) (Math.random() * 890));
    }
}
