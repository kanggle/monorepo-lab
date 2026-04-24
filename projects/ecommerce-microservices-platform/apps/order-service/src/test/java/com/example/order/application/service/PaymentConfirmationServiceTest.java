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
@DisplayName("PaymentConfirmationService 단위 테스트")
class PaymentConfirmationServiceTest {

    @InjectMocks
    private PaymentConfirmationService paymentConfirmationService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Clock clock;

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneOffset.UTC);
    private static final Instant PAID_AT = Instant.parse("2026-03-24T10:00:00Z");

    private Order createOrder() {
        return Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
    }

    @Test
    @DisplayName("정상적으로 결제 완료를 반영하고 저장한다")
    void markPaymentCompleted_validOrder_savesPaymentInfo() {
        Order order = createOrder();
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(Instant.parse("2026-03-25T10:00:00Z"));

        paymentConfirmationService.markPaymentCompleted(order.getOrderId(), "pay-123", PAID_AT);

        assertThat(order.getPaymentId()).isEqualTo("pay-123");
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("주문이 존재하지 않으면 warn 로그만 남기고 정상 완료한다")
    void markPaymentCompleted_orderNotFound_doesNotThrow() {
        given(orderRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatNoException().isThrownBy(() ->
                paymentConfirmationService.markPaymentCompleted("nonexistent", "pay-123", PAID_AT));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 결제 완료된 주문이면 저장하지 않는다")
    void markPaymentCompleted_alreadyCompleted_doesNotSave() {
        Order order = createOrder();
        order.markPaymentCompleted("pay-existing", PAID_AT, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        paymentConfirmationService.markPaymentCompleted(order.getOrderId(), "pay-456", PAID_AT);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("취소된 주문에 결제 완료 이벤트가 오면 warn 로그만 남기고 정상 완료한다")
    void markPaymentCompleted_cancelledOrder_doesNotThrow() {
        Order order = createOrder();
        order.cancel(FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        assertThatNoException().isThrownBy(() ->
                paymentConfirmationService.markPaymentCompleted(order.getOrderId(), "pay-123", PAID_AT));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("동일 paymentId로 2회 호출 시 멱등하게 처리한다")
    void markPaymentCompleted_duplicateCall_idempotent() {
        Order order = createOrder();
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(Instant.parse("2026-03-25T10:00:00Z"));

        paymentConfirmationService.markPaymentCompleted(order.getOrderId(), "pay-123", PAID_AT);
        paymentConfirmationService.markPaymentCompleted(order.getOrderId(), "pay-123", PAID_AT);

        assertThat(order.getPaymentId()).isEqualTo("pay-123");
        verify(orderRepository).save(order);
    }
}
