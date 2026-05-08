package com.wms.admin.application.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.projection.fakes.InMemoryDedupePort;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.outbound.OrderSummaryEntity;
import com.wms.admin.readmodel.outbound.OrderSummaryRepository;
import com.wms.admin.readmodel.outbound.ShipmentSummaryEntity;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboundProjectionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock OrderSummaryRepository orderRepo;
    @Mock ShipmentSummaryRepository shipmentRepo;
    @Mock ThroughputOutboundDailyRepository throughputRepo;
    @Mock PartnerRefRepository partnerRepo;

    private InMemoryDedupePort dedupe;
    private OutboundProjectionService service;

    @BeforeEach
    void setUp() {
        dedupe = new InMemoryDedupePort();
        ProjectionMetrics metrics = new ProjectionMetrics(new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new OutboundProjectionService(orderRepo, shipmentRepo, throughputRepo,
                partnerRepo, dedupe, metrics);
    }

    @Test
    void orderReceived_insertsRow() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("outbound.order.received",
                "wms.outbound.order.v1",
                "{\"orderId\":\"" + orderId + "\",\"orderNo\":\"ORD-1\",\"warehouseId\":\""
                        + warehouseId + "\",\"lines\":[{}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(orderRepo, times(1)).save(any(OrderSummaryEntity.class));
    }

    @Test
    void shippingConfirmed_insertsShipmentAndUpsertsCounter() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.empty());
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());
        when(throughputRepo.upsertIncrement(any(LocalDate.class), eq(warehouseId), anyInt(),
                any(Instant.class))).thenReturn(1);

        ProjectionEnvelope env = envelope("outbound.shipping.confirmed",
                "wms.outbound.shipping.confirmed.v1",
                "{\"shipmentId\":\"" + shipmentId + "\",\"orderId\":\"" + orderId
                        + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"shippedAt\":\"2026-05-09T15:00:00Z\","
                        + "\"lines\":[{\"qtyConfirmed\":100},{\"qtyConfirmed\":50}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(shipmentRepo, times(1)).save(any(ShipmentSummaryEntity.class));
        verify(throughputRepo, times(1)).upsertIncrement(
                eq(LocalDate.of(2026, 5, 9)), eq(warehouseId), eq(150), any(Instant.class));
    }

    @Test
    void orderCancelled_setsStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderSummaryEntity existing = new OrderSummaryEntity(orderId, "ORD-1",
                UUID.randomUUID(), null, null, "RECEIVED", null, null, 1, null,
                NOW.minusSeconds(60), null, NOW.minusSeconds(60));
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(existing));

        ProjectionEnvelope env = envelope("outbound.order.cancelled",
                "wms.outbound.order.v1",
                "{\"orderId\":\"" + orderId + "\",\"orderNo\":\"ORD-1\"}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        assertThat(existing.getStatus()).isEqualTo("CANCELLED");
    }

    private ProjectionEnvelope envelope(String eventType, String topic, String payloadJson)
            throws Exception {
        JsonNode payload = MAPPER.readTree(payloadJson);
        return new ProjectionEnvelope(UUID.randomUUID(), eventType, NOW, "agg", topic, payload);
    }
}
