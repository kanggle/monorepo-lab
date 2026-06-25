package com.example.shipping.infrastructure.event;

import com.example.shipping.domain.model.ShippingStatus;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringShippingEventPublisher 단위 테스트")
class SpringShippingEventPublisherTest {

    private SpringShippingEventPublisher publisher;

    @Mock
    private OutboxWriter outboxWriter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        publisher = new SpringShippingEventPublisher(outboxWriter, objectMapper, fixedClock);
    }

    @Test
    @DisplayName("ShippingStatusChanged 이벤트를 outbox에 저장한다")
    void publishShippingStatusChanged_savesToOutbox() {
        publisher.publishShippingStatusChanged(
                "tenant-a", "ship-1", "order-1", "user-1",
                ShippingStatus.PREPARING, ShippingStatus.SHIPPED,
                "TRK-001", "CJ대한통운");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("Shipping"), eq("ship-1"), eq("ShippingStatusChanged"), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"shippingId\":\"ship-1\"");
        assertThat(payload).contains("\"orderId\":\"order-1\"");
        assertThat(payload).contains("\"previousStatus\":\"PREPARING\"");
        assertThat(payload).contains("\"newStatus\":\"SHIPPED\"");
        assertThat(payload).contains("\"trackingNumber\":\"TRK-001\"");
        // M5: tenant_id stamped on the envelope top-level.
        assertThat(payload).contains("\"tenant_id\":\"tenant-a\"");
    }

    @Test
    @DisplayName("manual-confirm 이벤트를 wms camelCase 봉투로 outbox에 저장한다 (ManualShipConfirmRequested → 토픽)")
    void publishManualShipConfirmRequested_savesWmsEnvelopeToOutbox() {
        publisher.publishManualShipConfirmRequested("tenant-a", "order-1", "CJ대한통운", "TRK-001");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        // outbox routing key = "ManualShipConfirmRequested" (OutboxPollingScheduler maps it
        // to ecommerce.shipping.manual-confirm-requested.v1); aggregateId = orderId (D5 key).
        verify(outboxWriter).save(eq("Shipping"), eq("order-1"), eq("ManualShipConfirmRequested"),
                payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        // §26: assert the actual serialized wire shape (wms camelCase envelope).
        assertThat(payload).contains("\"eventType\":\"ecommerce.shipping.manual-confirm-requested\"");
        assertThat(payload).contains("\"aggregateType\":\"shipping\"");
        assertThat(payload).contains("\"aggregateId\":\"order-1\"");
        assertThat(payload).contains("\"tenantId\":\"tenant-a\"");
        assertThat(payload).contains("\"occurredAt\":\"2026-01-01T00:00:00Z\"");
        assertThat(payload).contains("\"eventId\":");
        // payload.orderNo == orderId (FulfillmentAcl invariant, D5 correlation key).
        assertThat(payload).contains("\"orderNo\":\"order-1\"");
        assertThat(payload).contains("\"carrierCode\":\"CJ대한통운\"");
        assertThat(payload).contains("\"trackingNo\":\"TRK-001\"");
    }

    @Test
    @DisplayName("manual-confirm: null carrier/tracking 도 봉투에 그대로 직렬화 (nullable wire fields)")
    void publishManualShipConfirmRequested_nullCarrierTracking_serializesNulls() {
        publisher.publishManualShipConfirmRequested("tenant-a", "order-2", null, null);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("Shipping"), eq("order-2"), eq("ManualShipConfirmRequested"),
                payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"orderNo\":\"order-2\"");
        assertThat(payload).contains("\"carrierCode\":null");
        assertThat(payload).contains("\"trackingNo\":null");
    }
}
