package com.example.scmplatform.logistics.integration;

import com.example.scmplatform.logistics.application.usecase.RetryDispatchUseCase;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Testcontainers Kafka IT for the live seam consumer (ADR-053 §D4; task AC — CI-authoritative).
 * Publishes real {@code outbound.shipping.confirmed} events and asserts the end-to-end loop:
 * routing, two-layer dedup, malformed → DLT, and the highest-signal
 * <b>vendor-failure-≠-consume-failure</b> contract.
 *
 * <p>Windows local SKIPs (no Docker); CI Linux is the gate — read the junit XML.
 */
class ShippingConfirmedConsumerIntegrationTest extends AbstractLogisticsIntegrationTest {

    private static final String SHIPMENTS = "/shipments";
    private static final String EASYPOST_BODY =
            "{\"id\":\"shp_1\",\"tracking_code\":\"TRACK-EP\","
                    + "\"selected_rate\":{\"carrier\":\"USPS\"},\"status\":\"purchased\"}";
    private static final String GOODSFLOW_BODY =
            "{\"id\":\"gf_1\",\"invoiceNo\":\"INV-GF\","
                    + "\"deliveryCompanyCode\":\"CJ-GLS\",\"status\":\"BOOKED\"}";

    @Autowired
    private RetryDispatchUseCase retryDispatchUseCase;

    @BeforeEach
    void reset() {
        cleanDatabase();
        EASYPOST.resetAll();
        GOODSFLOW.resetAll();
        stubBothVendorsSuccess();
    }

    private void stubBothVendorsSuccess() {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody(EASYPOST_BODY)));
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(GOODSFLOW_BODY)));
    }

    private Dispatch awaitDispatch(UUID shipmentId, DispatchStatus expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            Optional<Dispatch> d = persistencePort.findByShipmentId(shipmentId);
            assertThat(d).isPresent();
            assertThat(d.get().getStatus()).isEqualTo(expected);
        });
        return persistencePort.findByShipmentId(shipmentId).orElseThrow();
    }

    // ── Routing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void domesticCarrierCode_createsDispatch_routedToGoodsflow() {
        UUID shipmentId = UUID.randomUUID();
        publish(TOPIC, shipmentId.toString(),
                shippingConfirmedEnvelope(UUID.randomUUID(), TENANT_SCM, shipmentId, "SHP-KR", "CJ-LOGISTICS"));

        Dispatch d = awaitDispatch(shipmentId, DispatchStatus.DISPATCHED);
        assertThat(d.getVendor()).isEqualTo(Carrier.GOODSFLOW);
        assertThat(d.getTrackingNo().value()).isEqualTo("INV-GF");
        GOODSFLOW.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
        EASYPOST.verify(exactly(0), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void internationalCarrierCode_createsDispatch_routedToEasyPost() {
        UUID shipmentId = UUID.randomUUID();
        publish(TOPIC, shipmentId.toString(),
                shippingConfirmedEnvelope(UUID.randomUUID(), TENANT_SCM, shipmentId, "SHP-INTL", "UPS"));

        Dispatch d = awaitDispatch(shipmentId, DispatchStatus.DISPATCHED);
        assertThat(d.getVendor()).isEqualTo(Carrier.EASYPOST);
        assertThat(d.getTrackingNo().value()).isEqualTo("TRACK-EP");
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
        GOODSFLOW.verify(exactly(0), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void nullCarrierCode_createsDispatch_routedToDefaultVendor() {
        UUID shipmentId = UUID.randomUUID();
        // carrierCode omitted at source → CarrierRouter default vendor (EASYPOST) + CARRIER_UNROUTABLE degrade.
        publish(TOPIC, shipmentId.toString(),
                shippingConfirmedEnvelope(UUID.randomUUID(), TENANT_SCM, shipmentId, "SHP-NULL", null));

        Dispatch d = awaitDispatch(shipmentId, DispatchStatus.DISPATCHED);
        assertThat(d.getVendor()).isEqualTo(Carrier.EASYPOST);
        assertThat(d.getRequestedCarrierCode()).isNull(); // passed through raw, not coerced
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    // ── Two-layer dedup ───────────────────────────────────────────────────────────────────────

    @Test
    void duplicateEventId_layer1_noSecondDispatch() {
        UUID shipmentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String envelope = shippingConfirmedEnvelope(eventId, TENANT_SCM, shipmentId, "SHP-DUP-EVT", "UPS");

        publish(TOPIC, shipmentId.toString(), envelope);
        awaitDispatch(shipmentId, DispatchStatus.DISPATCHED);
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));

        // Redeliver the SAME eventId (Kafka redelivery / wms outbox retry) → T8 skip, no 2nd dispatch.
        publish(TOPIC, shipmentId.toString(), envelope);
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS))));
        assertThat(persistencePort.findByShipmentId(shipmentId)).isPresent();
    }

    @Test
    void newEventId_sameShipmentId_layer2_noDoubleDispatch() {
        UUID shipmentId = UUID.randomUUID();
        publish(TOPIC, shipmentId.toString(),
                shippingConfirmedEnvelope(UUID.randomUUID(), TENANT_SCM, shipmentId, "SHP-DUP-SHIP", "UPS"));
        awaitDispatch(shipmentId, DispatchStatus.DISPATCHED);
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));

        // wms republish: a NEW eventId for the SAME shipment. The shipment_id guard no-ops.
        UUID secondEventId = UUID.randomUUID();
        publish(TOPIC, shipmentId.toString(),
                shippingConfirmedEnvelope(secondEventId, TENANT_SCM, shipmentId, "SHP-DUP-SHIP", "UPS"));

        // Await proof the 2nd event was consumed (its eventId recorded), then assert no 2nd dispatch.
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                assertThat(processedEventExists(secondEventId)).isTrue());
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
        assertThat(persistencePort.findByShipmentId(shipmentId)).isPresent();
    }

    // ── Malformed → DLT (non-retryable) ─────────────────────────────────────────────────────────

    @Test
    void malformedEnvelope_nullEventId_routedToDlt_notDropped_noDispatch() {
        UUID shipmentId = UUID.randomUUID();
        String marker = "SHP-MALFORMED-" + UUID.randomUUID();
        // null eventId → invalid envelope → non-retryable → immediate DLT (raw value carries the marker).
        publish(TOPIC, shipmentId.toString(),
                shippingConfirmedEnvelope(null, TENANT_SCM, shipmentId, marker, "UPS"));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(dltRecordsContaining(marker, Duration.ofSeconds(2))).isGreaterThanOrEqualTo(1));

        // Not silently dropped AND no dispatch created.
        assertThat(persistencePort.findByShipmentId(shipmentId)).isEmpty();
    }

    // ── THE critical test: a vendor failure is ACK'd (not DLT), recoverable via :retry ──────────

    @Test
    void vendorFailure_isAckedNotDlt_dispatchFailed_thenRetryRecovers() {
        UUID shipmentId = UUID.randomUUID();
        String shipmentNo = "SHP-VENDOR-FAIL-" + UUID.randomUUID();

        // EasyPost 5xx-exhausts during consume (international UPS → EasyPost).
        EASYPOST.resetAll();
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS)).willReturn(aResponse().withStatus(503)));

        publish(TOPIC, shipmentId.toString(),
                shippingConfirmedEnvelope(UUID.randomUUID(), TENANT_SCM, shipmentId, shipmentNo, "UPS"));

        // The vendor failure is recorded DISPATCH_FAILED (NOT a consume failure).
        Dispatch failed = awaitDispatch(shipmentId, DispatchStatus.DISPATCH_FAILED);

        // The event was ACK'd (offset advanced) — it must NOT be on the DLT (task's key invariant).
        assertThat(dltRecordsContaining(shipmentNo, Duration.ofSeconds(3))).isZero();

        // Recovery: operator :retry re-drives the same shipment (EasyPost now healthy) → DISPATCHED.
        EASYPOST.resetAll();
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody(EASYPOST_BODY)));

        Dispatch recovered = retryDispatchUseCase.retry(failed.getId());
        assertThat(recovered.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(recovered.getVendor()).isEqualTo(Carrier.EASYPOST);
        assertThat(recovered.getTrackingNo().value()).isEqualTo("TRACK-EP");
    }

    private boolean processedEventExists(UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = CAST(? AS uuid)",
                Integer.class, eventId.toString());
        return count != null && count > 0;
    }
}
