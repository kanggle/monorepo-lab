package com.example.shipping.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.shipping.application.command.CancelShippingCommand;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.repository.ShippingRepository;
import com.example.shipping.domain.tenant.TenantContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OrderCancelledEventConsumer} (TASK-BE-649, ADR-MONO-022 D4 v2(d)).
 * The consumer only parses, dedupes, binds the tenant and delegates; the per-status decision
 * (PREPARING → CANCELLED, wmsRouted → cancel-request, SHIPPED+ / unknown → no-op) is pinned in
 * {@code ShippingCommandServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelledEventConsumer 단위 테스트 (TASK-BE-649)")
class OrderCancelledEventConsumerTest {

    @Mock
    private ShippingCommandService shippingCommandService;

    @Mock
    private ShippingRepository shippingRepository;

    @Mock
    private EventDedupePort eventDedupePort;

    /** Mirrors the Spring Boot mapper: unknown properties are ignored. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private OrderCancelledEventConsumer consumer;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        consumer = new OrderCancelledEventConsumer(
                shippingCommandService, shippingRepository, eventDedupePort, objectMapper);
        lenient().when(eventDedupePort.process(any(), eq("OrderCancelled"), any()))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return EventDedupePort.Outcome.APPLIED;
                });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private OrderCancelledEvent event(String tenantId, String orderId, String cancelledAt) {
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(), "OrderCancelled", "2026-10-23T10:00:00Z", "order-service", tenantId,
                new OrderCancelledEvent.OrderCancelledPayload(orderId, "user-1", cancelledAt, "OPERATOR"));
    }

    @Test
    @DisplayName("order-service 의 실제 봉투(snake_case + tenant_id)를 읽어 cancelByOrderId 로 넘긴다")
    void onMessage_wireJson_delegatesCancel() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String json = """
                {
                  "event_id": "%s",
                  "event_type": "OrderCancelled",
                  "occurred_at": "2026-10-23T10:00:01Z",
                  "source": "order-service",
                  "tenant_id": "tenant-a",
                  "payload": {
                    "orderId": "order-1",
                    "userId": "user-1",
                    "cancelledAt": "2026-10-23T10:00:00Z",
                    "cancelReason": "OPERATOR"
                  }
                }
                """.formatted(eventId);

        consumer.onMessage(json);

        verify(eventDedupePort).process(eq(UUID.fromString(eventId)), eq("OrderCancelled"), any());
        verify(shippingCommandService).cancelByOrderId(
                new CancelShippingCommand("order-1", Instant.parse("2026-10-23T10:00:00Z")));
    }

    @Test
    @DisplayName("중복 이벤트는 취소를 다시 부르지 않는다 (eventId dedupe)")
    void handle_duplicate_skips() {
        given(eventDedupePort.process(any(), eq("OrderCancelled"), any()))
                .willReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        consumer.handle(event("tenant-a", "order-1", "2026-10-23T10:00:00Z"));

        verify(shippingCommandService, never()).cancelByOrderId(any());
    }

    @Test
    @DisplayName("payload 가 null 이면 예외 없이 건너뛴다 (OrderConfirmed 소비자와 같음)")
    void handle_nullPayload_skips() {
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID().toString(), "OrderCancelled", "2026-10-23T10:00:00Z", "order-service",
                "tenant-a", null);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();

        verify(shippingCommandService, never()).cancelByOrderId(any());
    }

    @Test
    @DisplayName("orderId 가 비어 있으면 예외 없이 건너뛴다")
    void handle_blankOrderId_skips() {
        assertThatCode(() -> consumer.handle(event("tenant-a", " ", "2026-10-23T10:00:00Z")))
                .doesNotThrowAnyException();

        verify(shippingCommandService, never()).cancelByOrderId(any());
    }

    @Test
    @DisplayName("cancelledAt 을 읽을 수 없으면 null 로 넘긴다 (서비스가 자기 시계를 쓴다)")
    void handle_unparseableCancelledAt_passesNull() {
        consumer.handle(event("tenant-a", "order-1", "not-a-timestamp"));

        verify(shippingCommandService).cancelByOrderId(new CancelShippingCommand("order-1", null));
    }

    @Test
    @DisplayName("작업 중에는 봉투 tenant_id 가 TenantContext 에 묶이고, 끝나면 풀린다")
    void handle_bindsEnvelopeTenantDuringWork_thenClears() {
        AtomicReference<String> seen = new AtomicReference<>();
        willAnswer(inv -> {
            seen.set(TenantContext.currentTenant());
            return null;
        }).given(shippingCommandService).cancelByOrderId(any());

        consumer.handle(event("tenant-a", "order-1", "2026-10-23T10:00:00Z"));

        assertThat(seen.get()).isEqualTo("tenant-a");
        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
        verify(shippingRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("봉투에 tenant_id 가 없으면 배송 행의 tenant 로 묶는다")
    void handle_noEnvelopeTenant_fallsBackToRowTenant() {
        Shipping row = Shipping.create("tenant-b", "order-1", "user-1", CLOCK);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(row));
        AtomicReference<String> seen = new AtomicReference<>();
        willAnswer(inv -> {
            seen.set(TenantContext.currentTenant());
            return null;
        }).given(shippingCommandService).cancelByOrderId(any());

        consumer.handle(event(null, "order-1", "2026-10-23T10:00:00Z"));

        assertThat(seen.get()).isEqualTo("tenant-b");
    }

    @Test
    @DisplayName("서비스가 예외를 던져도 TenantContext 는 풀린다 (풀 스레드 누수 방지)")
    void handle_serviceThrows_stillClearsTenant() {
        willAnswer(inv -> {
            throw new IllegalStateException("boom");
        }).given(shippingCommandService).cancelByOrderId(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> consumer.handle(event("tenant-a", "order-1", "2026-10-23T10:00:00Z")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }
}
