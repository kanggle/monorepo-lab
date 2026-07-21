package com.example.order.presentation;

import com.example.order.TestOrderServiceApplication;
import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.dto.OrderDetail;
import com.example.order.application.dto.OrderSummary;
import com.example.order.application.dto.PlaceOrderResult;
import com.example.order.application.service.OrderCancellationService;
import com.example.order.application.service.OrderPlacementService;
import com.example.order.application.service.OrderQueryService;
import com.example.order.application.exception.UnauthorizedOrderAccessException;
import com.example.order.domain.exception.OrderCannotBeCancelledException;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = TestOrderServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderController 슬라이스 테스트")
class OrderControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPlacementService orderPlacementService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    private static final String VALID_PLACE_BODY = """
            {
              "items": [
                {
                  "productId": "p1",
                  "variantId": "v1",
                  "productName": "노트북",
                  "quantity": 1,
                  "unitPrice": 1000000
                }
              ],
              "shippingAddress": {
                "recipient": "홍길동",
                "phone": "010-1234-5678",
                "zipCode": "12345",
                "address1": "서울시 강남구"
              }
            }
            """;

    // ─── POST /api/orders ───────────────────────────────────────────────

    @Test
    @DisplayName("정상 요청 시 201과 orderId 반환")
    void placeOrder_validRequest_returns201() throws Exception {
        given(orderPlacementService.placeOrder(any())).willReturn(new PlaceOrderResult("order-123"));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PLACE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-123"));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 401 반환")
    void placeOrder_missingUserId_returns401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PLACE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("깨진 JSON 본문이면 400 / VALIDATION_ERROR 반환")
    void placeOrder_malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 빈 문자열이면 400 반환")
    void placeOrder_blankUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PLACE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("items가 비어있으면 400 반환")
    void placeOrder_emptyItems_returns400() throws Exception {
        String body = """
                {
                  "items": [],
                  "shippingAddress": {
                    "recipient": "홍길동", "phone": "010-1234-5678",
                    "zipCode": "12345", "address1": "서울시 강남구"
                  }
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("shippingAddress 누락 시 400 반환")
    void placeOrder_missingShippingAddress_returns400() throws Exception {
        String body = """
                {
                  "items": [
                    {"productId": "p1", "variantId": "v1", "productName": "노트북", "quantity": 1, "unitPrice": 1000}
                  ]
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ─── GET /api/orders ────────────────────────────────────────────────

    @Test
    @DisplayName("주문 목록 조회 시 200과 페이지네이션 응답 반환")
    void getOrders_validRequest_returns200() throws Exception {
        OrderSummary summary = new OrderSummary("order-1", OrderStatus.PENDING.name(), 1000L, 1, "상품A", Instant.now());
        given(orderQueryService.getOrders(eq("user1"), any(), any()))
                .willReturn(new PageResult<>(List.of(summary), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/orders").header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].orderId").value("order-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("status 파라미터 지정 시 해당 상태의 주문만 조회한다")
    void getOrders_withStatus_returns200() throws Exception {
        OrderSummary summary = new OrderSummary("order-1", OrderStatus.CONFIRMED.name(), 2000L, 1, "상품A", Instant.now());
        given(orderQueryService.getOrders(eq("user1"), eq(OrderStatus.CONFIRMED), any()))
                .willReturn(new PageResult<>(List.of(summary), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("status가 빈 문자열이면 전체 주문을 조회한다")
    void getOrders_emptyStatus_returnsAll() throws Exception {
        OrderSummary summary = new OrderSummary("order-1", OrderStatus.PENDING.name(), 1000L, 1, "상품A", Instant.now());
        given(orderQueryService.getOrders(eq("user1"), any(), any()))
                .willReturn(new PageResult<>(List.of(summary), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("status", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("유효하지 않은 status 값이면 400 반환")
    void getOrders_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
    }

    @Test
    @DisplayName("주문 목록 조회 시 X-User-Id 누락이면 401 반환")
    void getOrders_missingUserId_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("주문 목록 조회 시 X-User-Id 빈 문자열이면 400 반환")
    void getOrders_blankUserId_returns400() throws Exception {
        mockMvc.perform(get("/api/orders").header("X-User-Id", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ─── GET /api/orders — 페이지 사이즈 제한 ─────────────────────────────

    @Test
    @DisplayName("size가 최대값(100) 초과 시 100으로 클램핑된다")
    void getOrders_sizeExceedsMax_clampedTo100() throws Exception {
        given(orderQueryService.getOrders(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("size가 정확히 최대값(100)이면 그대로 사용된다")
    void getOrders_sizeEqualsMax_usedAsIs() throws Exception {
        given(orderQueryService.getOrders(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("size가 0이면 기본값(20)이 사용된다")
    void getOrders_sizeZero_defaultUsed() throws Exception {
        given(orderQueryService.getOrders(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("size가 음수이면 기본값(20)이 사용된다")
    void getOrders_sizeNegative_defaultUsed() throws Exception {
        given(orderQueryService.getOrders(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("size", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("정상 범위의 size는 그대로 사용된다")
    void getOrders_sizeInRange_usedAsIs() throws Exception {
        given(orderQueryService.getOrders(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("page가 음수이면 0으로 대체된다")
    void getOrders_pageNegative_replacedWithZero() throws Exception {
        given(orderQueryService.getOrders(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    // ─── GET /api/orders/verify-purchase ────────────────────────────────

    @Test
    @DisplayName("구매 확인 요청 시 purchased=true 반환")
    void verifyPurchase_purchased_returnsTrue() throws Exception {
        given(orderQueryService.hasUserPurchasedProduct("user1", "p1")).willReturn(true);

        mockMvc.perform(get("/api/orders/verify-purchase")
                        .header("X-User-Id", "user1")
                        .param("productId", "p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(true));
    }

    @Test
    @DisplayName("미구매 상품에 대해 purchased=false 반환")
    void verifyPurchase_notPurchased_returnsFalse() throws Exception {
        given(orderQueryService.hasUserPurchasedProduct("user1", "p2")).willReturn(false);

        mockMvc.perform(get("/api/orders/verify-purchase")
                        .header("X-User-Id", "user1")
                        .param("productId", "p2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(false));
    }

    @Test
    @DisplayName("productId 누락 시 400 반환")
    void verifyPurchase_missingProductId_returns400() throws Exception {
        mockMvc.perform(get("/api/orders/verify-purchase")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("X-User-Id 누락 시 401 반환")
    void verifyPurchase_missingUserId_returns401() throws Exception {
        mockMvc.perform(get("/api/orders/verify-purchase")
                        .param("productId", "p1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─── GET /api/orders/{orderId} ──────────────────────────────────────

    @Test
    @DisplayName("주문 상세 조회 시 200과 상세 정보 반환")
    void getOrder_validRequest_returns200() throws Exception {
        OrderDetail detail = new OrderDetail(
                "order-1", OrderStatus.PENDING.name(), 1000L,
                List.of(), new OrderDetail.ShippingAddressDetail("홍길동", "010", "12345", "서울", null),
                Instant.now(), Instant.now()
        );
        given(orderQueryService.getOrder(eq("order-1"), eq("user1"))).willReturn(detail);

        mockMvc.perform(get("/api/orders/order-1").header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"));
    }

    @Test
    @DisplayName("주문 상세 항목 응답에 seller_id 가 노출된다 (AC-7, 다중 셀러)")
    void getOrder_itemsExposeSellerId() throws Exception {
        OrderDetail detail = new OrderDetail(
                "order-1", OrderStatus.PENDING.name(), 1500L,
                List.of(
                        new OrderDetail.OrderItemDetail("p1", "v1", "노트북", "블랙", 1, 1000L, "seller-a1"),
                        new OrderDetail.OrderItemDetail("p2", "v2", "마우스", null, 1, 500L, "seller-a2")
                ),
                new OrderDetail.ShippingAddressDetail("홍길동", "010", "12345", "서울", null),
                Instant.now(), Instant.now()
        );
        given(orderQueryService.getOrder(eq("order-1"), eq("user1"))).willReturn(detail);

        mockMvc.perform(get("/api/orders/order-1").header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sellerId").value("seller-a1"))
                .andExpect(jsonPath("$.items[1].sellerId").value("seller-a2"));
    }

    @Test
    @DisplayName("다른 사용자 주문 조회 시 403 반환")
    void getOrder_unauthorizedUser_returns403() throws Exception {
        given(orderQueryService.getOrder(any(), any())).willThrow(new UnauthorizedOrderAccessException());

        mockMvc.perform(get("/api/orders/order-1").header("X-User-Id", "other"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 시 404 반환")
    void getOrder_notFound_returns404() throws Exception {
        given(orderQueryService.getOrder(any(), any())).willThrow(new OrderNotFoundException("order-x"));

        mockMvc.perform(get("/api/orders/order-x").header("X-User-Id", "user1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ─── POST /api/orders/{orderId}/cancel ──────────────────────────────

    @Test
    @DisplayName("주문 취소 성공 시 200과 CANCELLED 상태 반환")
    void cancelOrder_validRequest_returns200() throws Exception {
        given(orderCancellationService.cancelOrder(eq("order-1"), eq("user1")))
                .willReturn(new CancelOrderResult("order-1", OrderStatus.CANCELLED.name()));

        mockMvc.perform(post("/api/orders/order-1/cancel").header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("취소 불가 상태 주문 취소 시 422 반환")
    void cancelOrder_cannotBeCancelled_returns422() throws Exception {
        given(orderCancellationService.cancelOrder(any(), any()))
                .willThrow(new OrderCannotBeCancelledException("취소 불가"));

        mockMvc.perform(post("/api/orders/order-1/cancel").header("X-User-Id", "user1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_CANNOT_BE_CANCELLED"));
    }

    @Test
    @DisplayName("취소 시 X-User-Id 누락이면 401 반환")
    void cancelOrder_missingUserId_returns401() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("취소 시 X-User-Id 빈 문자열이면 400 반환")
    void cancelOrder_blankUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/cancel").header("X-User-Id", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ─── Optimistic Locking ──────────────────────────────────────────

    @Test
    @DisplayName("OptimisticLockingFailureException 발생 시 409 Conflict 반환")
    void cancelOrder_optimisticLockConflict_returns409() throws Exception {
        given(orderCancellationService.cancelOrder(any(), any()))
                .willThrow(new OptimisticLockingFailureException("Version conflict"));

        mockMvc.perform(post("/api/orders/order-1/cancel").header("X-User-Id", "user1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
