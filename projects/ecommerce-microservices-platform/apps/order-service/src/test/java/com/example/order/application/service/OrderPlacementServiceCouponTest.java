package com.example.order.application.service;

import com.example.order.application.dto.PlaceOrderCommand;
import com.example.order.application.dto.PlaceOrderResult;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.exception.CouponRejectedException;
import com.example.order.application.exception.CouponServiceUnavailableException;
import com.example.order.application.exception.DuplicateOrderPlacementException;
import com.example.order.application.port.CouponDiscountPort;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPlacementService 쿠폰 적용 (TASK-INT-026)")
class OrderPlacementServiceCouponTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderMetricsPort orderMetrics;

    @Mock
    private CouponDiscountPort couponDiscountPort;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);

    private OrderPlacementService service;

    private static final PlaceOrderCommand.ShippingAddressCommand ADDRESS =
            new PlaceOrderCommand.ShippingAddressCommand("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    /** subtotal = 1×20,000 + 2×5,000 = 30,000 */
    private static final List<PlaceOrderCommand.OrderItemCommand> ITEMS = List.of(
            new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", null, 1, 20000L),
            new PlaceOrderCommand.OrderItemCommand("p2", "v2", "마우스", null, 2, 5000L));

    @BeforeEach
    void setUp() {
        service = new OrderPlacementService(orderRepository, orderEventPublisher, orderMetrics, clock, couponDiscountPort);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static PlaceOrderCommand command(String idempotencyKey, String couponId) {
        return new PlaceOrderCommand("user-1", ITEMS, ADDRESS, idempotencyKey, couponId);
    }

    private static void completeTransaction(int status) {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(status);
        }
    }

    @Test
    @DisplayName("🔴 쿠폰 주문: 소계로 할인을 묻고, 응답과 OrderPlaced 의 totalPrice 가 할인 후 금액이다")
    void withCoupon_totalPriceIsServerDiscountedAmount_inResultAndEvent() {
        given(couponDiscountPort.applyCoupon(eq("coupon-1"), anyString(), eq("user-1"), eq(30000L)))
                .willReturn(5000L);

        PlaceOrderResult result = service.placeOrder(command(null, "coupon-1"));

        assertThat(result.totalPrice()).isEqualTo(25000L);
        assertThat(result.discountAmount()).isEqualTo(5000L);

        ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(couponDiscountPort).applyCoupon(eq("coupon-1"), orderIdCaptor.capture(), eq("user-1"), eq(30000L));
        assertThat(orderIdCaptor.getValue()).isEqualTo(result.orderId());

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventPublisher).publishOrderPlaced(eventCaptor.capture());
        OrderPlacedEvent.Payload payload = eventCaptor.getValue().payload();
        assertThat(payload.totalPrice()).isEqualTo(25000L);
        assertThat(payload.couponId()).isEqualTo("coupon-1");
        assertThat(payload.discountAmount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("쿠폰 없는 주문은 promotion-service 를 부르지 않고 금액이 그대로다")
    void withoutCoupon_neverCallsPromotion() {
        PlaceOrderResult result = service.placeOrder(command(null, null));

        verifyNoInteractions(couponDiscountPort);
        assertThat(result.totalPrice()).isEqualTo(30000L);
        assertThat(result.discountAmount()).isZero();

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventPublisher).publishOrderPlaced(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().couponId()).isNull();
        assertThat(eventCaptor.getValue().payload().discountAmount()).isZero();
        assertThat(eventCaptor.getValue().payload().totalPrice()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("쿠폰이 거절되면 주문을 저장하지도, OrderPlaced 를 발행하지도 않는다")
    void couponRejected_noSave_noEvent() {
        given(couponDiscountPort.applyCoupon(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(new CouponRejectedException("COUPON_ALREADY_USED", "used"));

        assertThatThrownBy(() -> service.placeOrder(command(null, "coupon-1")))
                .isInstanceOf(CouponRejectedException.class)
                .extracting("code").isEqualTo("COUPON_ALREADY_USED");

        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    @DisplayName("🔴 할인이 소계 전부면 COUPON_NOT_APPLICABLE 로 거절한다 — 0원 결제를 만들지 않는다")
    void discountCoveringWholeOrder_isNotApplicable() {
        given(couponDiscountPort.applyCoupon(anyString(), anyString(), anyString(), anyLong())).willReturn(30000L);

        assertThatThrownBy(() -> service.placeOrder(command(null, "coupon-1")))
                .isInstanceOf(CouponRejectedException.class)
                .extracting("code").isEqualTo("COUPON_NOT_APPLICABLE");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("promotion-service 무응답이면 주문을 만들지 않는다 — 할인 없이 진행하지 않는다")
    void promotionUnavailable_noOrder() {
        given(couponDiscountPort.applyCoupon(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(new CouponServiceUnavailableException("down", null));

        assertThatThrownBy(() -> service.placeOrder(command(null, "coupon-1")))
                .isInstanceOf(CouponServiceUnavailableException.class);

        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    @DisplayName("🔴 쿠폰을 적용한 뒤 배치가 커밋되지 않으면(동시 중복 키) 그 orderId 로 쿠폰 해제를 요청한다")
    void placementRolledBackAfterApply_releasesCouponForThatOrder() {
        TransactionSynchronizationManager.initSynchronization();
        given(couponDiscountPort.applyCoupon(anyString(), anyString(), anyString(), anyLong())).willReturn(5000L);
        given(orderRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.placeOrder(command("idem-1", "coupon-1")))
                .isInstanceOf(DuplicateOrderPlacementException.class);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(couponDiscountPort).applyCoupon(eq("coupon-1"), orderIdCaptor.capture(), anyString(), anyLong());
        verify(couponDiscountPort).releaseCoupon("coupon-1", orderIdCaptor.getValue());
    }

    @Test
    @DisplayName("🔴 apply 가 타임아웃이어도 해제를 요청한다 — promotion-service 에서는 커밋됐을 수 있다")
    void applyTimedOut_stillReleasesOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        given(couponDiscountPort.applyCoupon(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(new CouponServiceUnavailableException("timeout", null));

        assertThatThrownBy(() -> service.placeOrder(command(null, "coupon-1")))
                .isInstanceOf(CouponServiceUnavailableException.class);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(couponDiscountPort).applyCoupon(eq("coupon-1"), orderIdCaptor.capture(), anyString(), anyLong());
        verify(couponDiscountPort).releaseCoupon("coupon-1", orderIdCaptor.getValue());
    }

    @Test
    @DisplayName("배치가 커밋되면 해제하지 않는다")
    void placementCommitted_doesNotRelease() {
        TransactionSynchronizationManager.initSynchronization();
        given(couponDiscountPort.applyCoupon(anyString(), anyString(), anyString(), anyLong())).willReturn(5000L);

        service.placeOrder(command(null, "coupon-1"));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(couponDiscountPort, never()).releaseCoupon(anyString(), anyString());
    }

    @Test
    @DisplayName("멱등 재요청은 원래 주문의 금액을 돌려주고 promotion-service 를 다시 부르지 않는다")
    void idempotentReplay_returnsOriginalAmounts_withoutCallingPromotion() {
        Instant now = Instant.now(clock);
        Order original = Order.reconstitute("order-original", "user-1", List.of(), OrderStatus.PENDING, 25000L,
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", null),
                now, now, null, null, null, 0, null, 0L, "coupon-1", 5000L);
        given(orderRepository.findByUserIdAndIdempotencyKey("user-1", "idem-1")).willReturn(Optional.of(original));

        PlaceOrderResult result = service.placeOrder(command("idem-1", "coupon-1"));

        assertThat(result.orderId()).isEqualTo("order-original");
        assertThat(result.totalPrice()).isEqualTo(25000L);
        assertThat(result.discountAmount()).isEqualTo(5000L);
        verifyNoInteractions(couponDiscountPort);
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }
}
