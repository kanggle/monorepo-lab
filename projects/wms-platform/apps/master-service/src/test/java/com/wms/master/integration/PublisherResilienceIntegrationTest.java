package com.wms.master.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.master.adapter.out.messaging.OutboxMetrics;
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
 */
class PublisherResilienceIntegrationTest extends MasterServiceIntegrationBase {

    private static final String WRITE_ROLE = "MASTER_WRITE";
    private static final String TOPIC = "wms.master.warehouse.v1";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Kafka pause → outbox accumulates, counter rises; resume → rows drain")
    void outboxSurvivesKafkaOutage() throws Exception {
        double failuresBefore = failureCounter();

        // Step 1: pause Kafka (SIGSTOP inside container)
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            // Step 2: issue a mutation — commits to DB, outbox row written,
            // publisher retries hopelessly.
            String code = "WH" + shortSuffix();
            String body = """
                    {"warehouseCode":"%s","name":"Paused","timezone":"UTC"}
                    """.formatted(code);
            ResponseEntity<String> created = post("/api/v1/master/warehouses",
                    body, UUID.randomUUID().toString(), WRITE_ROLE);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Step 3: the DB-resident outbox row persists (no data loss).
            // We can't enter transactional context from here easily; instead we
            // poll the pending count from the gauge which queries the DB.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(pendingGauge()).isGreaterThanOrEqualTo(1.0d));

            // Failure counter must eventually increment (publisher tried and failed)
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(failureCounter()).isGreaterThan(failuresBefore));
        } finally {
            // Step 4: resume Kafka
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }

        // Step 5: outbox drains — pending count returns to zero and the event
        // lands on Kafka within the retry window.
        try (KafkaTestConsumer kafka = new KafkaTestConsumer(KAFKA.getBootstrapServers(), TOPIC)) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(pendingGauge()).isEqualTo(0.0d));
            // At least one record lands after resume
            assertThat(kafka.pollOne(Duration.ofSeconds(15))).isNotNull();
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
                meterRegistry.find(OutboxMetrics.PENDING_COUNT).gauge();
        return g == null ? -1.0 : g.value();
    }

    private double failureCounter() {
        io.micrometer.core.instrument.Counter c =
                meterRegistry.find(OutboxMetrics.PUBLISH_FAILURE_TOTAL).counter();
        return c == null ? 0.0 : c.count();
    }

    private static String shortSuffix() {
        return String.valueOf(10 + (int) (Math.random() * 890));
    }
}
