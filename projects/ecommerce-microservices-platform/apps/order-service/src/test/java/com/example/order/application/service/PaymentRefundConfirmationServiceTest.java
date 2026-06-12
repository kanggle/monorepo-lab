package com.example.order.application.service;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRefundConfirmationService 단위 테스트")
class PaymentRefundConfirmationServiceTest {

    @InjectMocks
    private PaymentRefundConfirmationService paymentRefundConfirmationService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Clock clock;

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneOffset.UTC);
    private static final Instant REFUNDED_AT = Instant.parse("2026-03-24T12:00:00Z");

    private Order createCancelledOrder() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.cancel(FIXED_CLOCK);
        return order;
    }

    @Test
    @DisplayName("정상적으로 환불을 반영하고 저장한다")
    void markRefunded_cancelledOrder_savesRefundInfo() {
        Order order = createCancelledOrder();
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(Instant.parse("2026-03-25T10:00:00Z"));

        paymentRefundConfirmationService.markRefunded(order.getOrderId(), REFUNDED_AT);

        assertThat(order.getRefundedAt()).isEqualTo(REFUNDED_AT);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("주문이 존재하지 않으면 warn 로그만 남기고 정상 완료한다")
    void markRefunded_orderNotFound_doesNotThrow() {
        given(orderRepository.findByIdAcrossTenants("nonexistent")).willReturn(Optional.empty());

        assertThatNoException().isThrownBy(() ->
                paymentRefundConfirmationService.markRefunded("nonexistent", REFUNDED_AT));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 환불 처리된 주문이면 저장하지 않는다")
    void markRefunded_alreadyRefunded_doesNotSave() {
        Order order = createCancelledOrder();
        order.markRefunded(REFUNDED_AT, FIXED_CLOCK);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));

        paymentRefundConfirmationService.markRefunded(order.getOrderId(), Instant.now());

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("PENDING 상태의 주문에 환불 이벤트가 오면 예외가 전파되지 않는다")
    void markRefunded_pendingOrder_doesNotThrow() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));

        assertThatNoException().isThrownBy(() ->
                paymentRefundConfirmationService.markRefunded(order.getOrderId(), REFUNDED_AT));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("CONFIRMED 상태의 주문에 환불 이벤트가 오면 예외가 전파되지 않는다")
    void markRefunded_confirmedOrder_doesNotThrow() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.markPaymentCompleted("pay-123", Instant.now(), FIXED_CLOCK);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));

        assertThatNoException().isThrownBy(() ->
                paymentRefundConfirmationService.markRefunded(order.getOrderId(), REFUNDED_AT));

        verify(orderRepository, never()).save(any());
    }
}
