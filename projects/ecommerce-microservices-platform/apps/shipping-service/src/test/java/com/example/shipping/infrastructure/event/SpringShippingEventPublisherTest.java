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
}
