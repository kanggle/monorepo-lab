package com.example.shipping.infrastructure.event;

import com.example.shipping.domain.model.ShippingStatus;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the {@link SpringShippingEventPublisher} write path (TASK-BE-446,
 * outbox v2). Asserts each event persists a {@code shipping_outbox} row whose
 * wire-relevant fields are preserved exactly: the routing-key {@code eventType},
 * the {@code aggregate_type}/{@code aggregate_id} (Kafka key source), and the
 * byte-identical serialized envelope payload (or, for the forward fulfillment leg,
 * the opaque {@code messageJson} stored verbatim).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpringShippingEventPublisher 단위 테스트 (outbox v2)")
class SpringShippingEventPublisherTest {

    private SpringShippingEventPublisher publisher;

    @Mock
    private ShippingOutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        publisher = new SpringShippingEventPublisher(outboxRepository, objectMapper, fixedClock);
    }

    @Test
    @DisplayName("ShippingStatusChanged 이벤트를 shipping_outbox v2 행으로 저장한다")
    void publishShippingStatusChanged_savesV2Row() {
        publisher.publishShippingStatusChanged(
                "tenant-a", "ship-1", "order-1", "user-1",
                ShippingStatus.PREPARING, ShippingStatus.SHIPPED,
                "TRK-001", "CJ대한통운");

        ShippingOutboxEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo("ShippingStatusChanged");
        assertThat(row.getAggregateType()).isEqualTo("Shipping");
        assertThat(row.getAggregateId()).isEqualTo("ship-1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getEventId()).isNotNull();
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));

        String payload = row.getPayload();
        assertThat(payload).contains("\"shippingId\":\"ship-1\"");
        assertThat(payload).contains("\"orderId\":\"order-1\"");
        assertThat(payload).contains("\"previousStatus\":\"PREPARING\"");
        assertThat(payload).contains("\"newStatus\":\"SHIPPED\"");
        assertThat(payload).contains("\"trackingNumber\":\"TRK-001\"");
        assertThat(payload).contains("\"tenant_id\":\"tenant-a\"");
        // row event_id reuses the envelope event_id (header == payload id)
        assertThat(payload).contains("\"event_id\":\"" + row.getEventId() + "\"");
    }

    /**
     * TASK-BE-547 — the event_id must be a deterministic function of
     * {@code (shippingId, newStatus)}, not a fresh random UUID. This is what makes the
     * downstream {@code event_id} dedup actually collapse a concurrent double-transition
     * (and, because event_id == the outbox PK, what makes the second concurrent publish
     * PK-collide). A random id per call silently defeats both.
     */
    @Test
    @DisplayName("BE-547: ShippingStatusChanged event_id 는 (shippingId, newStatus) 에서 결정적으로 채번된다")
    void publishShippingStatusChanged_eventId_isDeterministicPerTransition() {
        publisher.publishShippingStatusChanged(
                "tenant-a", "ship-1", "order-1", "user-1",
                ShippingStatus.PREPARING, ShippingStatus.SHIPPED, "TRK-001", "CJ");
        String firstId = capturedRow().getEventId().toString();

        // same (shippingId, newStatus) — even with different previousStatus/tracking/carrier/tenant —
        // must reproduce the same id (a concurrent retry of the SAME transition).
        reset(outboxRepository);
        publisher.publishShippingStatusChanged(
                "tenant-b", "ship-1", "order-1", "user-1",
                ShippingStatus.SHIPPED, ShippingStatus.SHIPPED, "OTHER-TRK", "HANJIN");
        String sameTransitionId = capturedRow().getEventId().toString();

        // a genuinely different transition of the same shipment must get a DIFFERENT id
        // (SHIPPED-notification and DELIVERED-notification must both go through — AC-3).
        reset(outboxRepository);
        publisher.publishShippingStatusChanged(
                "tenant-a", "ship-1", "order-1", "user-1",
                ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED, "TRK-001", "CJ");
        String differentTransitionId = capturedRow().getEventId().toString();

        // a different shipment reaching the same status must also get a different id
        reset(outboxRepository);
        publisher.publishShippingStatusChanged(
                "tenant-a", "ship-2", "order-2", "user-2",
                ShippingStatus.PREPARING, ShippingStatus.SHIPPED, "TRK-002", "CJ");
        String otherShipmentSameStatusId = capturedRow().getEventId().toString();

        assertThat(sameTransitionId)
                .as("same (shippingId, newStatus) must reproduce the same event_id")
                .isEqualTo(firstId);
        assertThat(differentTransitionId)
                .as("a different newStatus on the same shipment must get a different event_id")
                .isNotEqualTo(firstId);
        assertThat(otherShipmentSameStatusId)
                .as("a different shipment reaching the same status must get a different event_id")
                .isNotEqualTo(firstId);
    }

    @Test
    @DisplayName("FulfillmentRequested: opaque messageJson 을 그대로 payload 로 저장한다")
    void publishFulfillmentRequested_storesMessageJsonVerbatim() {
        String messageJson = "{\"eventId\":\"abc\",\"eventType\":\"ecommerce.fulfillment.requested\",\"payload\":{\"orderNo\":\"order-9\"}}";

        publisher.publishFulfillmentRequested("order-9", messageJson);

        ShippingOutboxEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo("FulfillmentRequested");
        assertThat(row.getAggregateType()).isEqualTo("Fulfillment");
        assertThat(row.getAggregateId()).isEqualTo("order-9");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getEventId()).isNotNull();
        // payload byte-identical to the supplied messageJson
        assertThat(row.getPayload()).isEqualTo(messageJson);
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("manual-confirm 이벤트를 wms camelCase 봉투로 shipping_outbox 에 저장한다")
    void publishManualShipConfirmRequested_savesWmsEnvelopeV2Row() {
        publisher.publishManualShipConfirmRequested("tenant-a", "order-1", "CJ대한통운", "TRK-001");

        ShippingOutboxEntity row = capturedRow();
        // routing key = "ManualShipConfirmRequested"; aggregateId = orderId (D5 key)
        assertThat(row.getEventType()).isEqualTo("ManualShipConfirmRequested");
        assertThat(row.getAggregateType()).isEqualTo("Shipping");
        assertThat(row.getAggregateId()).isEqualTo("order-1");

        String payload = row.getPayload();
        assertThat(payload).contains("\"eventType\":\"ecommerce.shipping.manual-confirm-requested\"");
        assertThat(payload).contains("\"aggregateType\":\"shipping\"");
        assertThat(payload).contains("\"aggregateId\":\"order-1\"");
        assertThat(payload).contains("\"tenantId\":\"tenant-a\"");
        assertThat(payload).contains("\"occurredAt\":\"2026-01-01T00:00:00Z\"");
        assertThat(payload).contains("\"orderNo\":\"order-1\"");
        assertThat(payload).contains("\"carrierCode\":\"CJ대한통운\"");
        assertThat(payload).contains("\"trackingNo\":\"TRK-001\"");
    }

    @Test
    @DisplayName("manual-confirm: null carrier/tracking 도 봉투에 그대로 직렬화 (nullable wire fields)")
    void publishManualShipConfirmRequested_nullCarrierTracking_serializesNulls() {
        publisher.publishManualShipConfirmRequested("tenant-a", "order-2", null, null);

        String payload = capturedRow().getPayload();
        assertThat(payload).contains("\"orderNo\":\"order-2\"");
        assertThat(payload).contains("\"carrierCode\":null");
        assertThat(payload).contains("\"trackingNo\":null");
    }

    private ShippingOutboxEntity capturedRow() {
        ArgumentCaptor<ShippingOutboxEntity> captor = ArgumentCaptor.forClass(ShippingOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
