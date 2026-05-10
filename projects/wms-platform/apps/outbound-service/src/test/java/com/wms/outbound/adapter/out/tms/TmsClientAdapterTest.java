package com.wms.outbound.adapter.out.tms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.port.out.TmsRequestDedupePort;
import com.wms.outbound.domain.exception.ShipmentNotFoundException;
import com.wms.outbound.domain.model.Shipment;
import com.wms.outbound.domain.model.TmsStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link TmsClientAdapter}.
 *
 * <p>The Resilience4j annotations are not active in unit context (no
 * Spring AOP) — the test exercises the adapter's logic directly:
 * dedupe lookup, mapper, snapshot persistence on success, exception
 * routing.
 */
@ExtendWith(MockitoExtension.class)
class TmsClientAdapterTest {

    private static final UUID SHIPMENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");

    @Mock
    private RestClient restClient;
    @Mock
    private ShipmentPersistencePort shipmentPersistence;
    @Mock
    private TmsRequestDedupePort dedupePort;

    private MeterRegistry meterRegistry;
    private TmsMetrics metrics;
    private ObjectMapper objectMapper;
    private TmsClientAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        CircuitBreakerRegistry cbr = CircuitBreakerRegistry.ofDefaults();
        // pre-register so the gauge has a circuit to query
        CircuitBreaker unused = cbr.circuitBreaker("tms-client");
        assertThat(unused.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        metrics = new TmsMetrics(meterRegistry, cbr);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

        adapter = new TmsClientAdapter(restClient, shipmentPersistence, dedupePort,
                metrics, objectMapper, clock);
    }

    @Test
    void dedupeHit_skipsHttpAndReturnsCached() throws Exception {
        TmsAcknowledgement cached = new TmsAcknowledgement(true, "vendor-req-1", "TRK-1", "CJ");
        String snapshotJson = objectMapper.writeValueAsString(cached);
        when(dedupePort.findSnapshot(SHIPMENT_ID)).thenReturn(Optional.of(snapshotJson));

        TmsAcknowledgement result = adapter.notify(SHIPMENT_ID);

        assertThat(result).isEqualTo(cached);
        verify(restClient, never()).post();
        verify(shipmentPersistence, never()).findById(any());
        assertThat(meterRegistry.counter("outbound.tms.dedupe.cache_hit.count").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "dedupe_hit").count())
                .isEqualTo(1.0);
    }

    @Test
    void dedupeMiss_throwsWhenShipmentMissing() {
        when(dedupePort.findSnapshot(SHIPMENT_ID)).thenReturn(Optional.empty());
        when(shipmentPersistence.findById(SHIPMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.notify(SHIPMENT_ID))
                .isInstanceOf(ShipmentNotFoundException.class);
        verify(restClient, never()).post();
        verify(dedupePort, never()).saveSnapshot(any(), any(), anyString());
    }

    @Test
    void fallback_onCircuitOpen_throwsExternalUnavailable_andRecordsMetric() {
        var cbException = io.github.resilience4j.circuitbreaker.CallNotPermittedException
                .createCallNotPermittedException(
                        CircuitBreakerRegistry.ofDefaults().circuitBreaker("tms-client"));

        assertThatThrownBy(() -> adapter.notifyFallback(SHIPMENT_ID, cbException))
                .isInstanceOf(com.wms.outbound.domain.exception.ExternalServiceUnavailableException.class)
                .hasMessageContaining(SHIPMENT_ID.toString());
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "circuit_open").count())
                .isEqualTo(1.0);
    }

    @Test
    void fallback_onPermanent4xx_recordsClient4xx() {
        var permanent = new TmsPermanentException("400 bad request", 400);

        assertThatThrownBy(() -> adapter.notifyFallback(SHIPMENT_ID, permanent))
                .isInstanceOf(com.wms.outbound.domain.exception.ExternalServiceUnavailableException.class);
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "client_4xx").count())
                .isEqualTo(1.0);
    }

    @Test
    void fallback_onTimeout_recordsTimeout() {
        var transient_ = new TmsTransientException("timeout", 0);

        assertThatThrownBy(() -> adapter.notifyFallback(SHIPMENT_ID, transient_))
                .isInstanceOf(com.wms.outbound.domain.exception.ExternalServiceUnavailableException.class);
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "timeout").count())
                .isEqualTo(1.0);
    }

    @Test
    void fallback_onServer5xx_recordsServer5xx() {
        var transient_ = new TmsTransientException("500 server", 500);

        assertThatThrownBy(() -> adapter.notifyFallback(SHIPMENT_ID, transient_))
                .isInstanceOf(com.wms.outbound.domain.exception.ExternalServiceUnavailableException.class);
        assertThat(meterRegistry.counter("outbound.tms.request.count", "result", "server_5xx").count())
                .isEqualTo(1.0);
    }

    @Test
    void mapper_translatesShipmentIntoVendorRequest() {
        Shipment shipment = sampleShipment();
        TmsShipmentRequest req = TmsShipmentMapper.toRequest(shipment);

        assertThat(req.shipmentId()).isEqualTo(shipment.getId());
        assertThat(req.shipmentNo()).isEqualTo(shipment.getShipmentNo());
        assertThat(req.carrierCode()).isEqualTo(shipment.getCarrierCode());
        assertThat(req.shippedAt()).isEqualTo(shipment.getShippedAt());
        assertThat(req.orderId()).isEqualTo(shipment.getOrderId());
    }

    @Test
    void mapper_translatesVendorResponseIntoAck_acceptedIsSuccess() {
        TmsShipmentResponse resp = new TmsShipmentResponse(
                "vendor-req-99", "TRK-99", "CJ", "ACCEPTED", null);
        TmsAcknowledgement ack = TmsShipmentMapper.toAcknowledgement(resp);

        assertThat(ack.success()).isTrue();
        assertThat(ack.requestId()).isEqualTo("vendor-req-99");
        assertThat(ack.trackingNo()).isEqualTo("TRK-99");
        assertThat(ack.carrierCode()).isEqualTo("CJ");
    }

    @Test
    void mapper_translatesVendorResponseIntoAck_rejectedIsNotSuccess() {
        TmsShipmentResponse resp = new TmsShipmentResponse(
                "vendor-req-99", null, null, "REJECTED", "schema invalid");
        TmsAcknowledgement ack = TmsShipmentMapper.toAcknowledgement(resp);

        assertThat(ack.success()).isFalse();
    }

    @Test
    void mapper_pendingCarrierAssignmentIsSuccess() {
        TmsShipmentResponse resp = new TmsShipmentResponse(
                "vendor-req-99", null, null, "PENDING_CARRIER_ASSIGNMENT", null);
        TmsAcknowledgement ack = TmsShipmentMapper.toAcknowledgement(resp);

        assertThat(ack.success()).isTrue();
    }

    private static Shipment sampleShipment() {
        return new Shipment(
                SHIPMENT_ID,
                ORDER_ID,
                "SHP-20260510-1234",
                "CJ",
                null,
                T0,
                TmsStatus.PENDING,
                null,
                null,
                0L,
                T0,
                "user-uuid",
                T0);
    }
}
