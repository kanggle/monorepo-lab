package com.example.shipping.infrastructure.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.messaging.dedupe.EventDedupePort;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import com.example.shipping.domain.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link WmsOutboundCancelledConsumer} (TASK-MONO-196,
 * ADR-MONO-022 §D4, ADR-MONO-058 D7). v1 is alert-only — the consumer raises an ops alert and
 * intentionally does NOT mutate the Shipping (stays PREPARING; auto-refund/cancel
 * saga = v2). These tests pin the v1 contract: dedupe is honored, and neither a
 * duplicate nor a null payload throws (no DLT for those).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WmsOutboundCancelledConsumer 단위 테스트 (ADR-MONO-058 D7)")
class WmsOutboundCancelledConsumerTest {

    @InjectMocks
    private WmsOutboundCancelledConsumer consumer;

    @Mock
    private ShippingRepository shippingRepository;

    @Mock
    private EventDedupePort eventDedupePort;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void stubDedupeAppliesByDefault() {
        lenient().when(eventDedupePort.process(any(), eq("WmsOutboundCancelled"), any()))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return EventDedupePort.Outcome.APPLIED;
                });
    }

    private Logger consumerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureLogs() {
        consumerLogger = (Logger) LoggerFactory.getLogger(WmsOutboundCancelledConsumer.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        consumerLogger.addAppender(logAppender);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        consumerLogger.detachAppender(logAppender);
    }

    private List<ILoggingEvent> alerts() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().startsWith("ALERT"))
                .toList();
    }

    private WmsOutboundCancelledEvent event(String reason) {
        return new WmsOutboundCancelledEvent(
                UUID.randomUUID().toString(), "outbound.order.cancelled", "2026-06-08T11:30:00Z",
                "order", "wms-internal-1", "store-acme",
                new WmsOutboundCancelledEvent.Payload("order-1", "PICKING", reason, "2026-06-08T11:30:00Z"));
    }

    @Test
    @DisplayName("정상 backorder 이벤트는 dedupe 확인 후 알림만 (예외 없음, 상태 변경 없음)")
    void handle_validBackorder_alertsOnly() {
        WmsOutboundCancelledEvent event = event("INSUFFICIENT_STOCK");

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();

        verify(eventDedupePort).process(any(), eq("WmsOutboundCancelled"), any());
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다 (eventId dedupe)")
    void handle_duplicate_skips() {
        WmsOutboundCancelledEvent event = event("INSUFFICIENT_STOCK");
        given(eventDedupePort.process(any(), eq("WmsOutboundCancelled"), any()))
                .willReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("payload가 null이어도 예외 없이 graceful skip (DLT 아님)")
    void handle_nullPayload_gracefulSkip() {
        WmsOutboundCancelledEvent event = new WmsOutboundCancelledEvent(
                UUID.randomUUID().toString(), "outbound.order.cancelled", "2026-06-08T11:30:00Z",
                "order", "wms-internal-1", "store-acme", null);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("alert 후 TenantContext 는 finally 에서 clear 된다 (스레드 누수 방지)")
    void handle_clearsTenantAfterAlert() {
        WmsOutboundCancelledEvent event = event("INSUFFICIENT_STOCK");

        consumer.handle(event);

        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    // ─── TASK-BE-649 — suppress the echo of our own cancel request (ADR-MONO-022 D4 v2(d)) ──

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("BE-649: 배송이 이미 CANCELLED 면 ALERT 를 내지 않는다 (자기가 요청한 취소의 메아리)")
    void handle_shippingAlreadyCancelled_suppressesAlert() {
        Shipping cancelled = Shipping.create("store-acme", "order-1", "user-1", CLOCK);
        cancelled.markWmsRouted();
        cancelled.cancel(CLOCK);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(cancelled));

        consumer.handle(event("ORDER_CANCELLED"));

        assertThat(alerts()).isEmpty();
        assertThat(logAppender.list)
                .anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(Level.INFO);
                    assertThat(e.getFormattedMessage()).contains("order-1");
                });
        assertThat(cancelled.getStatus()).isEqualTo(ShippingStatus.CANCELLED);
        verify(shippingRepository, never()).save(any());
    }

    @Test
    @DisplayName("BE-649: 배송이 PREPARING 이면 (backorder 등) 여전히 ALERT")
    void handle_shippingPreparing_stillAlerts() {
        Shipping preparing = Shipping.create("store-acme", "order-1", "user-1", CLOCK);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(preparing));

        consumer.handle(event("INSUFFICIENT_STOCK"));

        assertThat(alerts()).hasSize(1);
        assertThat(alerts().get(0).getFormattedMessage()).contains("orderNo=order-1");
        verify(shippingRepository, never()).save(any());
    }

    @Test
    @DisplayName("BE-649: 배송 행이 없어도 (backorder 시점) 여전히 ALERT")
    void handle_noShippingRow_stillAlerts() {
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.empty());

        consumer.handle(event("INSUFFICIENT_STOCK"));

        assertThat(alerts()).hasSize(1);
    }

    @Test
    @DisplayName("BE-649: reason 이 ORDER_CANCELLED 여도 배송이 CANCELLED 가 아니면 ALERT (판정은 배송 상태)")
    void handle_orderCancelledReasonButShippingNotCancelled_stillAlerts() {
        Shipping shipped = Shipping.create("store-acme", "order-1", "user-1", CLOCK);
        shipped.transitionTo(ShippingStatus.SHIPPED, "TRK-1", "CJ", CLOCK);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(shipped));

        consumer.handle(event("ORDER_CANCELLED"));

        assertThat(alerts()).hasSize(1);
    }
}
