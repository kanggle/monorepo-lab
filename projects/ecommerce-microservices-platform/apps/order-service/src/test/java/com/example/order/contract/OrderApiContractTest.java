package com.example.order.contract;

import com.example.order.TestOrderServiceApplication;
import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.dto.OrderDetail;
import com.example.order.application.dto.OrderSummary;
import com.example.order.application.dto.PlaceOrderResult;
import com.example.order.application.service.OrderCancellationService;
import com.example.order.application.service.OrderPlacementService;
import com.example.order.application.service.OrderQueryService;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageResult;
import com.example.order.presentation.GlobalExceptionHandler;
import com.example.order.presentation.OrderController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.example.order.contract.ContractTestHelper.assertFieldsMatch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * order-service API 응답 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/http/order-api.md
 *
 * <p>{@link ContextConfiguration} pins the test bootstrap to
 * {@link TestOrderServiceApplication} so Spring's {@code AnnotatedClassFinder}
 * does not pick between {@code OrderServiceApplication} (main) and
 * {@code TestOrderServiceApplication} (test) — both carry
 * {@code @SpringBootApplication}, and without an explicit pin the slice
 * fails with "Found multiple @SpringBootConfiguration annotated classes".
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = TestOrderServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("Order API 컨트랙트 테스트 — specs/contracts/http/order-api.md")
class OrderApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderPlacementService orderPlacementService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    private static final String SPEC_REF = "specs/contracts/http/order-api.md";

    // ─── POST /api/orders — 201 ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders 성공 응답은 {orderId}만 포함한다")
    void placeOrder_response_containsOnlyOrderId() throws Exception {
        given(orderPlacementService.placeOrder(any())).willReturn(new PlaceOrderResult("order-1"));

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlaceOrderBody()))
                .andExpect(status().isCreated())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("orderId"), SPEC_REF + " POST /api/orders 201");
    }

    // ─── GET /api/orders — 200 ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders 응답은 {content, page, size, totalElements}만 포함한다")
    void getOrders_response_containsSpecFields() throws Exception {
        OrderSummary summary = new OrderSummary("order-1", OrderStatus.PENDING.name(), 1000L, 1, "상품A", Instant.now());
        given(orderQueryService.getOrders(eq("user-1"), any(), any()))
                .willReturn(new PageResult<>(List.of(summary), 0, 20, 1L, 1));

        MvcResult result = mockMvc.perform(get("/api/orders").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertFieldsMatch(json, Set.of("content", "page", "size", "totalElements"),
                SPEC_REF + " GET /api/orders 200");

        JsonNode content = objectMapper.readTree(json).get("content").get(0);
        assertFieldsMatch(content, Set.of("orderId", "status", "totalPrice", "itemCount", "firstItemName", "createdAt"),
                SPEC_REF + " GET /api/orders 200 content[]");
    }

    // ─── GET /api/orders/{orderId} — 200 ────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{orderId} 응답은 스펙 정의 필드만 포함한다")
    void getOrderDetail_response_containsSpecFields() throws Exception {
        OrderDetail detail = new OrderDetail(
                "order-1", OrderStatus.PENDING.name(), 30000L,
                List.of(new OrderDetail.OrderItemDetail("p1", "v1", "노트북", "옵션A", 2, 15000L)),
                new OrderDetail.ShippingAddressDetail("홍길동", "010-1234-5678", "12345", "서울시", "강남구"),
                Instant.now(), Instant.now()
        );
        given(orderQueryService.getOrder(eq("order-1"), eq("user-1"))).willReturn(detail);

        MvcResult result = mockMvc.perform(get("/api/orders/order-1").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        assertFieldsMatch(root, Set.of("orderId", "status", "totalPrice", "items", "shippingAddress", "createdAt", "updatedAt"),
                SPEC_REF + " GET /api/orders/{orderId} 200");

        JsonNode item = root.get("items").get(0);
        assertFieldsMatch(item, Set.of("productId", "variantId", "productName", "optionName", "quantity", "unitPrice", "sellerId"),
                SPEC_REF + " GET /api/orders/{orderId} 200 items[]");

        JsonNode addr = root.get("shippingAddress");
        assertFieldsMatch(addr, Set.of("recipient", "phone", "zipCode", "address1", "address2"),
                SPEC_REF + " GET /api/orders/{orderId} 200 shippingAddress");
    }

    // ─── GET /api/orders/verify-purchase — 200 ─────────────────────────

    @Test
    @DisplayName("GET /api/orders/verify-purchase 응답은 {purchased}만 포함한다")
    void verifyPurchase_response_containsOnlyPurchased() throws Exception {
        given(orderQueryService.hasUserPurchasedProduct("user-1", "p1")).willReturn(true);

        MvcResult result = mockMvc.perform(get("/api/orders/verify-purchase")
                        .header("X-User-Id", "user-1")
                        .param("productId", "p1"))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("purchased"), SPEC_REF + " GET /api/orders/verify-purchase 200");
    }

    // ─── POST /api/orders/{orderId}/cancel — 200 ────────────────────────

    @Test
    @DisplayName("POST /api/orders/{orderId}/cancel 응답은 {orderId, status}만 포함한다")
    void cancelOrder_response_containsOnlyOrderIdAndStatus() throws Exception {
        given(orderCancellationService.cancelOrder(eq("order-1"), eq("user-1")))
                .willReturn(new CancelOrderResult("order-1", OrderStatus.CANCELLED.name()));

        MvcResult result = mockMvc.perform(post("/api/orders/order-1/cancel").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("orderId", "status"), SPEC_REF + " POST /api/orders/{orderId}/cancel 200");
    }

    // ─── Error Response Format ──────────────────────────────────────────

    @Test
    @DisplayName("에러 응답은 {code, message, timestamp}만 포함한다")
    void errorResponse_containsOnlyCodeMessageTimestamp() throws Exception {
        given(orderQueryService.getOrder(any(), any())).willThrow(new OrderNotFoundException("order-x"));

        MvcResult result = mockMvc.perform(get("/api/orders/order-x").header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("code", "message", "timestamp"),
                "specs/platform/error-handling.md error format");
    }

    private String validPlaceOrderBody() {
        return """
                {
                  "items": [
                    {"productId": "p1", "variantId": "v1", "productName": "노트북", "quantity": 1, "unitPrice": 1000000}
                  ],
                  "shippingAddress": {
                    "recipient": "홍길동", "phone": "010-1234-5678",
                    "zipCode": "12345", "address1": "서울시 강남구"
                  }
                }
                """;
    }
}
