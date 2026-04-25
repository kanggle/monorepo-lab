package com.wms.master.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.master.adapter.out.messaging.OutboxMetrics;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * Verifies the three OutboxMetrics meter families are present in the
 * /actuator/prometheus scrape body.
 *
 * <p>Lives in its own class so that {@link DirtiesContext} can force a fresh
 * Spring context just for this assertion. The assertion was originally a
 * method on {@link WarehouseIntegrationTest} but turned out to fail in CI
 * whenever {@link PublisherResilienceIntegrationTest} ran first in the same
 * cached context: pausing/unpausing the Kafka container leaves
 * micrometer-kafka client meters in a transient state that suppresses the
 * outbox meter family from the scrape body output (HTTP 200 returned, body
 * contains other families but not {@code outbox_pending_count} et al.).
 *
 * <p>By isolating this class with {@code @DirtiesContext(BEFORE_CLASS)}, the
 * scrape always runs against a context where Kafka client meters are stable
 * and outbox meters are eagerly registered (TASK-BE-020 strong reference +
 * base-class @Autowired field). The startup cost of one extra context refresh
 * is acceptable for the integration suite.
 */
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class OutboxPrometheusScrapeIntegrationTest extends MasterServiceIntegrationBase {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("prometheus actuator endpoint exposes the three outbox metrics")
    void prometheusEndpoint_exposesOutboxMetrics() {
        // Permit-all endpoint per SecurityConfig — no auth.
        // Retry for up to 30 s to absorb startup variance on CI runners.
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ResponseEntity<String> response =
                            rest.getForEntity("/actuator/prometheus", String.class);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    String body = response.getBody();
                    assertThat(body).contains(OutboxMetrics.PENDING_COUNT.replace('.', '_'));
                    assertThat(body).contains(
                            OutboxMetrics.PUBLISH_SUCCESS_TOTAL.replace('.', '_'));
                    assertThat(body).contains(
                            OutboxMetrics.PUBLISH_FAILURE_TOTAL.replace('.', '_'));
                });
    }
}
