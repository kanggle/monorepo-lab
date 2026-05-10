package com.wms.outbound.integration.tms;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.RetryTmsNotificationCommand;
import com.wms.outbound.application.port.in.RetryTmsNotificationUseCase;
import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.result.RetryTmsNotificationResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.exception.ExternalServiceUnavailableException;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.Shipment;
import com.wms.outbound.domain.model.TmsStatus;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.integration.OutboundServiceIntegrationBase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * WireMock + Testcontainers integration tests for the real TMS adapter
 * (TASK-BE-049). Covers the 6 scenarios mandated by Acceptance Criteria
 * and {@code architecture.md} § Testing Requirements > TMS Adapter:
 *
 * <ol>
 *   <li>Success → ack stored in {@code tms_request_dedupe}, saga
 *       {@code COMPLETED}.</li>
 *   <li>Timeout → 3 retries → caller surfaces
 *       {@link ExternalServiceUnavailableException}.</li>
 *   <li>5xx → 3 retries → same.</li>
 *   <li>4xx → no retry → same; only 1 wiremock call.</li>
 *   <li>Circuit-breaker open → fast-fail without HTTP call.</li>
 *   <li>Manual retry endpoint → success on second attempt → saga
 *       {@code COMPLETED}.</li>
 * </ol>
 */
class TmsClientAdapterIT extends OutboundServiceIntegrationBase {

    @Autowired private ShipmentNotificationPort tmsPort;
    @Autowired private RetryTmsNotificationUseCase retryUseCase;
    @Autowired private ShipmentPersistencePort shipmentPersistence;
    @Autowired private SagaPersistencePort sagaPersistence;
    @Autowired private OutboundSagaCoordinator sagaCoordinator;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    private static final Instant NOW = Instant.parse("2026-05-10T10:00:00Z");

    private UUID shipmentId;
    private UUID sagaId;
    private UUID orderId;

    @BeforeEach
    void resetState() {
        WIREMOCK.resetAll();
        // reset circuit so each test starts CLOSED
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tms-client");
        cb.reset();
        // clean dedupe + shipment + saga
        jdbc.update("DELETE FROM tms_request_dedupe");
        jdbc.update("DELETE FROM shipment");
        jdbc.update("DELETE FROM outbound_saga");
        // shipment + saga rows for the test
        shipmentId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        sagaId = UUID.randomUUID();
        seedShipmentRow(shipmentId, orderId, "SHP-20260510-0001", TmsStatus.PENDING);
        seedSagaRow(sagaId, orderId, SagaStatus.SHIPPED);
    }

    @AfterEach
    void resetCircuit() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tms-client");
        cb.reset();
    }

    // ------------------------------------------------------------------
    //  Scenario 1 — success
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scenario 1: success → ack stored, dedupe row written")
    void successAckStored() {
        WIREMOCK.stubFor(post(urlPathEqualTo("/tms/shipments"))
                .withHeader("Idempotency-Key", equalTo(shipmentId.toString()))
                .withHeader("X-Tms-Api-Key", equalTo("test-api-key"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tmsRequestId\":\"vendor-1\",\"trackingNumber\":\"TRK-1\","
                                + "\"carrierCode\":\"CJ\",\"status\":\"ACCEPTED\"}")));

        TmsAcknowledgement ack = tmsPort.notify(shipmentId);

        assertThat(ack.success()).isTrue();
        assertThat(ack.requestId()).isEqualTo("vendor-1");
        assertThat(ack.trackingNo()).isEqualTo("TRK-1");
        // dedupe row was persisted
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM tms_request_dedupe WHERE request_id = ?",
                Long.class, shipmentId);
        assertThat(count).isEqualTo(1L);
        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo("/tms/shipments")));
        // success metric incremented
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "success").count())
                .isGreaterThanOrEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    //  Scenario 2 — timeout
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scenario 2: timeout → 3 retries → ExternalServiceUnavailableException")
    void timeoutRetriesThreeTimes() {
        // delay (3s) > readTimeout (2s) so each call times out
        WIREMOCK.stubFor(post(urlPathEqualTo("/tms/shipments"))
                .willReturn(aResponse().withFixedDelay(3_000).withStatus(200)));

        assertThatThrownBy(() -> tmsPort.notify(shipmentId))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        // 3 attempts hit WireMock
        WIREMOCK.verify(3, postRequestedFor(urlPathEqualTo("/tms/shipments")));
        // dedupe row was NOT written
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM tms_request_dedupe", Long.class);
        assertThat(count).isEqualTo(0L);
    }

    // ------------------------------------------------------------------
    //  Scenario 3 — 5xx
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scenario 3: 5xx → 3 retries → ExternalServiceUnavailableException")
    void serverErrorRetriesThreeTimes() {
        WIREMOCK.stubFor(post(urlPathEqualTo("/tms/shipments"))
                .willReturn(aResponse().withStatus(500).withBody("internal error")));

        assertThatThrownBy(() -> tmsPort.notify(shipmentId))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        WIREMOCK.verify(3, postRequestedFor(urlPathEqualTo("/tms/shipments")));
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "server_5xx").count())
                .isGreaterThanOrEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    //  Scenario 4 — 4xx (no retry)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scenario 4: 4xx → no retry → ExternalServiceUnavailableException")
    void clientErrorDoesNotRetry() {
        WIREMOCK.stubFor(post(urlPathEqualTo("/tms/shipments"))
                .willReturn(aResponse().withStatus(400).withBody("bad request")));

        assertThatThrownBy(() -> tmsPort.notify(shipmentId))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo("/tms/shipments")));
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "client_4xx").count())
                .isGreaterThanOrEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    //  Scenario 5 — circuit open fast-fails
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scenario 5: circuit open → fast-fail, no HTTP call")
    void circuitOpenFastFails() {
        // Trigger enough 5xx failures to open the circuit (sliding window 4,
        // minimum 2 calls).
        WIREMOCK.stubFor(post(urlPathEqualTo("/tms/shipments"))
                .willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 4; i++) {
            try {
                tmsPort.notify(shipmentId);
            } catch (ExternalServiceUnavailableException ignored) {
            }
        }
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tms-client");
        // After the burst the breaker should be OPEN.
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .until(() -> cb.getState() == CircuitBreaker.State.OPEN);

        int callsBefore = WIREMOCK.getAllServeEvents().size();
        assertThatThrownBy(() -> tmsPort.notify(shipmentId))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        int callsAfter = WIREMOCK.getAllServeEvents().size();
        assertThat(callsAfter).isEqualTo(callsBefore); // no new HTTP call
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "circuit_open").count())
                .isGreaterThanOrEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    //  Scenario 6 — manual retry succeeds on next attempt
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scenario 6: manual retry success → saga COMPLETED")
    void manualRetrySucceeds() {
        // pre-condition: shipment is in NOTIFY_FAILED, saga in
        // SHIPPED_NOT_NOTIFIED — operators have already exhausted the
        // first-pass retries and clicked "Retry" in the ops console.
        jdbc.update("UPDATE shipment SET tms_status = ?, status = ? WHERE id = ?",
                "NOTIFY_FAILED", "NOTIFY_FAILED", shipmentId);
        jdbc.update("UPDATE outbound_saga SET state = ?, failure_reason = ? WHERE saga_id = ?",
                "SHIPPED_NOT_NOTIFIED", "TMS notify exhausted", sagaId);

        // WireMock now returns a clean ack.
        WIREMOCK.stubFor(post(urlPathEqualTo("/tms/shipments"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tmsRequestId\":\"vendor-retry-1\",\"trackingNumber\":\"TRK-RETRY\","
                                + "\"carrierCode\":\"CJ\",\"status\":\"ACCEPTED\"}")));

        RetryTmsNotificationResult result = retryUseCase.retry(new RetryTmsNotificationCommand(
                shipmentId, "user-admin", Set.of("ROLE_OUTBOUND_ADMIN")));

        assertThat(result.tmsStatus()).isEqualTo("NOTIFIED");
        assertThat(result.sagaState()).isEqualTo("COMPLETED");
        // shipment row updated
        String tmsStatus = jdbc.queryForObject(
                "SELECT tms_status FROM shipment WHERE id = ?", String.class, shipmentId);
        assertThat(tmsStatus).isEqualTo("NOTIFIED");
        // saga row updated
        String sagaState = jdbc.queryForObject(
                "SELECT state FROM outbound_saga WHERE saga_id = ?", String.class, sagaId);
        assertThat(sagaState).isEqualTo("COMPLETED");
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void seedShipmentRow(UUID id, UUID order, String shipmentNo, TmsStatus status) {
        jdbc.update("INSERT INTO shipment (id, order_id, shipment_no, carrier, tracking_number, "
                        + "status, shipped_at, tms_status, tms_notified_at, tms_request_id, "
                        + "created_at, created_by, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, NULL, ?, ?, ?, NULL, NULL, ?, ?, ?, 0)",
                id, order, shipmentNo, "CJ", status.name(), java.sql.Timestamp.from(NOW),
                status.name(), java.sql.Timestamp.from(NOW), "user-it", java.sql.Timestamp.from(NOW));
    }

    private void seedSagaRow(UUID id, UUID order, SagaStatus state) {
        jdbc.update("INSERT INTO outbound_saga (saga_id, order_id, state, picking_request_id, "
                        + "failure_reason, started_at, last_transition_at, version) "
                        + "VALUES (?, ?, ?, ?, NULL, ?, ?, 0)",
                id, order, state.name(), id,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }
}
