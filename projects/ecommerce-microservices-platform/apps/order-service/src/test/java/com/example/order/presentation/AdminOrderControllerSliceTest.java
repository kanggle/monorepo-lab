package com.example.order.presentation;

import com.example.order.TestOrderServiceApplication;
import com.example.order.application.dto.AdminOrderDetail;
import com.example.order.application.dto.AdminOrderStatusChangeResult;
import com.example.order.application.dto.AdminOrderSummary;
import com.example.order.application.dto.OrderDetail;
import com.example.order.application.service.AdminOrderStatusService;
import com.example.order.application.service.OrderQueryService;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageResult;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = TestOrderServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminOrderController 슬라이스 테스트")
class AdminOrderControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private AdminOrderStatusService adminOrderStatusService;

    // ─── GET /api/admin/orders ───────────────────────────────────────────

    @Test
    @DisplayName("업무 역할 헤더로 주문 목록 조회 시 200과 주문 목록 반환 (operator-plane, TASK-MONO-795 서비스 재검사)")
    void getOrders_businessRoleHeader_returns200WithOrderList() throws Exception {
        AdminOrderSummary summary = new AdminOrderSummary(
                "order-1", "user-1", OrderStatus.PENDING.name(),
                50000L, 2, "노트북", Instant.now()
        );
        given(orderQueryService.getAllOrders(any(), any()))
                .willReturn(new PageResult<>(List.of(summary), 0, 20, 1L, 1));

        // TASK-MONO-795: a business-role header — the service now re-checks it (was: no X-User-Role header) — operator-plane: authz is the gateway's
        // OPERATOR + tenant_id + WHERE tenant_id, not this controller.
        mockMvc.perform(get("/api/admin/orders").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].orderId").value("order-1"))
                .andExpect(jsonPath("$.content[0].userId").value("user-1"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("status 파라미터 PENDING으로 필터링 시 200 반환")
    void getOrders_withStatusFilter_returns200() throws Exception {
        AdminOrderSummary summary = new AdminOrderSummary(
                "order-2", "user-2", OrderStatus.PENDING.name(),
                30000L, 1, "마우스", Instant.now()
        );
        given(orderQueryService.getAllOrders(eq(OrderStatus.PENDING), any()))
                .willReturn(new PageResult<>(List.of(summary), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/admin/orders").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ─── GET /api/admin/orders/{orderId} ────────────────────────────────

    @Test
    @DisplayName("관리자 역할로 주문 상세 조회 시 200과 상세 정보 반환")
    void getOrder_adminRole_returns200WithOrderDetail() throws Exception {
        OrderDetail.ShippingAddressDetail address = new OrderDetail.ShippingAddressDetail(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구", null
        );
        AdminOrderDetail detail = new AdminOrderDetail(
                "order-1", "user-1", OrderStatus.PENDING.name(),
                50000L, List.of(), address, Instant.now(), Instant.now()
        );
        given(orderQueryService.getOrderForAdmin(eq("order-1"))).willReturn(detail);

        mockMvc.perform(get("/api/admin/orders/order-1").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 상세 조회 시 404 반환")
    void getOrder_orderNotFound_returns404() throws Exception {
        given(orderQueryService.getOrderForAdmin(any()))
                .willThrow(new OrderNotFoundException("order-x"));

        mockMvc.perform(get("/api/admin/orders/order-x").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ─── POST /api/admin/orders/{orderId}/status ─────────────────────────

    @Test
    @DisplayName("유효한 상태 CANCELLED로 변경 시 200과 변경된 상태 반환 (operator-plane, TASK-MONO-795 서비스 재검사 · 운영자가 정할 수 있는 유일한 상태 — TASK-BE-648)")
    void changeStatus_validStatus_returns200WithUpdatedStatus() throws Exception {
        given(adminOrderStatusService.changeStatus(eq("order-1"), eq("CANCELLED")))
                .willReturn(new AdminOrderStatusChangeResult("order-1", "CANCELLED"));

        // TASK-MONO-795: a business-role header — the service now re-checks it (was: no X-User-Role header) — operator-plane write: authz is the gateway's
        // OPERATOR + tenant_id + WHERE tenant_id, not this controller.
        mockMvc.perform(post("/api/admin/orders/order-1/status").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PENDING 주문을 운영자가 CONFIRMED로 변경 요청 시 400 INVALID_ORDER_REQUEST (BE-645 와 같은 오류 형태 — TASK-BE-648)")
    void changeStatus_pendingToConfirmed_returns400InvalidOrderRequest() throws Exception {
        given(adminOrderStatusService.changeStatus(eq("order-p"), eq("CONFIRMED")))
                .willThrow(new com.example.order.domain.exception.InvalidOrderException(
                        "An order cannot be confirmed by an operator (current status: PENDING); "
                                + "orders are confirmed only by the payment / stock-reservation saga. "
                                + "An operator may only cancel an order (from PENDING, CONFIRMED or BACKORDERED)"));

        mockMvc.perform(post("/api/admin/orders/order-p/status").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot be confirmed by an operator")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("유효하지 않은 상태 값 INVALID로 변경 요청 시 400 반환")
    void changeStatus_invalidStatus_returns400() throws Exception {
        given(adminOrderStatusService.changeStatus(any(), eq("INVALID")))
                .willThrow(new com.example.order.application.exception.InvalidOrderStatusException("INVALID"));

        mockMvc.perform(post("/api/admin/orders/order-1/status").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "INVALID"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
    }

    @Test
    @DisplayName("BACKORDERED 주문을 CONFIRMED로 변경 요청 시 400 INVALID_ORDER_REQUEST (기존 오류 형태 — TASK-BE-645)")
    void changeStatus_backorderedToConfirmed_returns400InvalidOrderRequest() throws Exception {
        given(adminOrderStatusService.changeStatus(eq("order-b"), eq("CONFIRMED")))
                .willThrow(new com.example.order.domain.exception.InvalidOrderException(
                        "A BACKORDERED order cannot be confirmed by an operator; "
                                + "it is confirmed by the restock re-reservation, or may be cancelled"));

        mockMvc.perform(post("/api/admin/orders/order-b/status").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("BACKORDERED")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("status 필드가 빈 문자열인 요청 시 400 유효성 검사 오류 반환")
    void changeStatus_blankStatus_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/orders/order-1/status").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 상태 변경 시 404 반환")
    void changeStatus_orderNotFound_returns404() throws Exception {
        given(adminOrderStatusService.changeStatus(any(), any()))
                .willThrow(new OrderNotFoundException("order-x"));

        mockMvc.perform(post("/api/admin/orders/order-x/status").header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
