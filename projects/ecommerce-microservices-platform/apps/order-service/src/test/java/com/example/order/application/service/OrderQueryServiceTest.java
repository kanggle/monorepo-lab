package com.example.order.application.service;

import com.example.order.application.dto.OrderDetail;
import com.example.order.application.dto.OrderSummary;
import com.example.order.application.exception.UnauthorizedOrderAccessException;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService 단위 테스트")
class OrderQueryServiceTest {

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Mock
    private OrderRepository orderRepository;

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneOffset.UTC);

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    private static final PageQuery DEFAULT_PAGE_QUERY = new PageQuery(0, 20, "createdAt", "DESC");

    @Test
    @DisplayName("status 미지정 시 전체 주문 목록이 반환된다")
    void getOrders_noStatus_returnsSummaries() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        PageResult<Order> orderPage = new PageResult<>(List.of(order), 0, 20, 1L, 1);
        given(orderRepository.findByUserId("user1", DEFAULT_PAGE_QUERY)).willReturn(orderPage);

        PageResult<OrderSummary> result = orderQueryService.getOrders("user1", null, DEFAULT_PAGE_QUERY);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(OrderStatus.PENDING.name());
    }

    @Test
    @DisplayName("status 지정 시 해당 상태의 주문만 반환된다")
    void getOrders_withStatus_returnsFilteredSummaries() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        PageResult<Order> orderPage = new PageResult<>(List.of(order), 0, 20, 1L, 1);
        given(orderRepository.findByUserIdAndStatus("user1", OrderStatus.PENDING, DEFAULT_PAGE_QUERY))
                .willReturn(orderPage);

        PageResult<OrderSummary> result = orderQueryService.getOrders("user1", OrderStatus.PENDING, DEFAULT_PAGE_QUERY);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(OrderStatus.PENDING.name());
    }

    @Test
    @DisplayName("status 지정 시 결과가 없으면 빈 페이지를 반환한다")
    void getOrders_withStatusNoResults_returnsEmptyPage() {
        PageResult<Order> emptyPage = new PageResult<>(List.of(), 0, 20, 0L, 0);
        given(orderRepository.findByUserIdAndStatus("user1", OrderStatus.SHIPPED, DEFAULT_PAGE_QUERY))
                .willReturn(emptyPage);

        PageResult<OrderSummary> result = orderQueryService.getOrders("user1", OrderStatus.SHIPPED, DEFAULT_PAGE_QUERY);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("주문 상세 조회 시 소유자가 맞으면 OrderDetail을 반환한다")
    void getOrder_ownerRequests_returnsDetail() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 2, 5000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        OrderDetail detail = orderQueryService.getOrder(order.getOrderId(), "user1");

        assertThat(detail.orderId()).isEqualTo(order.getOrderId());
        assertThat(detail.totalPrice()).isEqualTo(10000L);
        assertThat(detail.items()).hasSize(1);
    }

    @Test
    @DisplayName("다른 사용자가 조회하면 UnauthorizedOrderAccessException이 발생한다")
    void getOrder_differentUser_throwsUnauthorized() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderQueryService.getOrder(order.getOrderId(), "user2"))
                .isInstanceOf(UnauthorizedOrderAccessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 orderId 조회 시 OrderNotFoundException이 발생한다")
    void getOrder_notFound_throwsOrderNotFoundException() {
        given(orderRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderQueryService.getOrder("nonexistent", "user1"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ─── hasUserPurchasedProduct ──────────────────────────────────────

    @Test
    @DisplayName("배송 완료된 주문에 해당 상품이 포함되어 있으면 true를 반환한다")
    void hasUserPurchasedProduct_delivered_returnsTrue() {
        given(orderRepository.existsByUserIdAndProductIdAndStatus("user1", "p1", OrderStatus.DELIVERED))
                .willReturn(true);

        boolean result = orderQueryService.hasUserPurchasedProduct("user1", "p1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("배송 완료된 주문에 해당 상품이 없으면 false를 반환한다")
    void hasUserPurchasedProduct_notDelivered_returnsFalse() {
        given(orderRepository.existsByUserIdAndProductIdAndStatus("user1", "p1", OrderStatus.DELIVERED))
                .willReturn(false);

        boolean result = orderQueryService.hasUserPurchasedProduct("user1", "p1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("주문이 없는 사용자의 구매 확인은 false를 반환한다")
    void hasUserPurchasedProduct_noOrders_returnsFalse() {
        given(orderRepository.existsByUserIdAndProductIdAndStatus("user-no-orders", "p1", OrderStatus.DELIVERED))
                .willReturn(false);

        boolean result = orderQueryService.hasUserPurchasedProduct("user-no-orders", "p1");

        assertThat(result).isFalse();
    }
}
