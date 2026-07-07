package com.example.shipping.interfaces.rest.controller;

import com.example.shipping.TestShippingServiceApplication;
import com.example.web.exception.AccessDeniedException;
import com.example.shipping.application.exception.UnauthorizedShippingAccessException;
import com.example.shipping.application.result.ShippingResult;
import com.example.shipping.application.result.ShippingSummary;
import com.example.shipping.application.result.UpdateShippingStatusResult;
import com.example.shipping.application.service.RefreshTrackingService;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.application.service.ShippingQueryService;
import com.example.shipping.domain.exception.InvalidShippingException;
import com.example.shipping.domain.exception.InvalidStatusTransitionException;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.ShippingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@WebMvcTest(ShippingController.class)
@ContextConfiguration(classes = TestShippingServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ShippingController 슬라이스 테스트")
class ShippingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShippingCommandService shippingCommandService;

    @MockitoBean
    private ShippingQueryService shippingQueryService;

    @MockitoBean
    private RefreshTrackingService refreshTrackingService;

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    // ─── GET /api/shippings/orders/{orderId} ─────────────────────────

    @Test
    @DisplayName("주문 ID로 배송 조회 성공 시 200 반환")
    void getShippingByOrderId_validRequest_returns200() throws Exception {
        ShippingResult result = new ShippingResult(
                "ship-1", "order-1", ShippingStatus.PREPARING, null, null, true,
                List.of(new ShippingResult.StatusHistoryEntryResult(ShippingStatus.PREPARING, NOW)),
                NOW, NOW);
        given(shippingQueryService.getShippingByOrderId("order-1", "user-1")).willReturn(result);

        mockMvc.perform(get("/api/shippings/orders/order-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingId").value("ship-1"))
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.wmsRouted").value(true))
                .andExpect(jsonPath("$.statusHistory[0].status").value("PREPARING"));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 401 반환")
    void getShippingByOrderId_missingUserId_returns401() throws Exception {
        mockMvc.perform(get("/api/shippings/orders/order-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 배송 조회 시 404 반환")
    void getShippingByOrderId_notFound_returns404() throws Exception {
        given(shippingQueryService.getShippingByOrderId("order-x", "user-1"))
                .willThrow(new ShippingNotFoundException("order-x"));

        mockMvc.perform(get("/api/shippings/orders/order-x")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPPING_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 사용자가 배송 조회 시 403 반환")
    void getShippingByOrderId_differentUser_returns403() throws Exception {
        given(shippingQueryService.getShippingByOrderId("order-1", "other-user"))
                .willThrow(new UnauthorizedShippingAccessException("User does not have access to this shipping record"));

        mockMvc.perform(get("/api/shippings/orders/order-1")
                        .header("X-User-Id", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ─── PUT /api/shippings/{shippingId}/status ──────────────────────

    @Test
    @DisplayName("배송 상태 업데이트 성공 시 200 반환")
    void updateShippingStatus_validRequest_returns200() throws Exception {
        UpdateShippingStatusResult result = new UpdateShippingStatusResult("ship-1", ShippingStatus.SHIPPED, NOW);
        given(shippingCommandService.updateStatus(any())).willReturn(result);

        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-001", "carrier": "CJ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingId").value("ship-1"))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("deductWmsInventory=true 본문이 command 로 전달된다")
    void updateShippingStatus_deductWmsInventory_propagatedToCommand() throws Exception {
        UpdateShippingStatusResult result = new UpdateShippingStatusResult("ship-1", ShippingStatus.SHIPPED, NOW);
        org.mockito.ArgumentCaptor<com.example.shipping.application.command.UpdateShippingStatusCommand> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.shipping.application.command.UpdateShippingStatusCommand.class);
        given(shippingCommandService.updateStatus(captor.capture())).willReturn(result);

        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-001", "carrier": "CJ", "deductWmsInventory": true}
                                """))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().deductWmsInventory()).isTrue();
    }

    @Test
    @DisplayName("deductWmsInventory 미지정 시 command 는 false (기본값)")
    void updateShippingStatus_deductWmsInventoryAbsent_defaultsFalse() throws Exception {
        UpdateShippingStatusResult result = new UpdateShippingStatusResult("ship-1", ShippingStatus.SHIPPED, NOW);
        org.mockito.ArgumentCaptor<com.example.shipping.application.command.UpdateShippingStatusCommand> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.shipping.application.command.UpdateShippingStatusCommand.class);
        given(shippingCommandService.updateStatus(captor.capture())).willReturn(result);

        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-001", "carrier": "CJ"}
                                """))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().deductWmsInventory()).isFalse();
    }

    // ─── POST /api/shippings/{shippingId}/refresh-tracking (TASK-BE-293) ──

    @Test
    @DisplayName("carrier refresh 성공 시 200 + 갱신된 상태 반환")
    void refreshTracking_admin_returns200() throws Exception {
        UpdateShippingStatusResult result =
                new UpdateShippingStatusResult("ship-1", ShippingStatus.DELIVERED, NOW);
        given(refreshTrackingService.refreshFromCarrier(eq("ship-1"), eq("ECOMMERCE_OPERATOR"))).willReturn(result);

        mockMvc.perform(post("/api/shippings/ship-1/refresh-tracking")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingId").value("ship-1"))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("carrier refresh X-User-Role 누락 시 401 반환")
    void refreshTracking_missingUserRole_returns401() throws Exception {
        mockMvc.perform(post("/api/shippings/ship-1/refresh-tracking"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("깨진 JSON 본문이면 400 / VALIDATION_ERROR 반환")
    void updateShippingStatus_malformedBody_returns400() throws Exception {
        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("X-User-Role 헤더 누락 시 401 반환")
    void updateShippingStatus_missingUserRole_returns401() throws Exception {
        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-001", "carrier": "CJ"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("비관리자가 상태 업데이트 시 403 반환")
    void updateShippingStatus_nonAdmin_returns403() throws Exception {
        given(shippingCommandService.updateStatus(any()))
                .willThrow(new AccessDeniedException("Admin role required"));

        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-001", "carrier": "CJ"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("status가 null이면 400 반환")
    void updateShippingStatus_nullStatus_returns400() throws Exception {
        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNumber": "TRK-001", "carrier": "CJ"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("유효하지 않은 status 값이면 400 반환")
    void updateShippingStatus_invalidStatus_returns400() throws Exception {
        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "INVALID_STATUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHIPPING_REQUEST"));
    }

    @Test
    @DisplayName("존재하지 않는 배송 ID로 상태 업데이트 시 404 반환")
    void updateShippingStatus_notFound_returns404() throws Exception {
        given(shippingCommandService.updateStatus(any()))
                .willThrow(new ShippingNotFoundException("ship-x"));

        mockMvc.perform(put("/api/shippings/ship-x/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-001", "carrier": "CJ"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPPING_NOT_FOUND"));
    }

    @Test
    @DisplayName("잘못된 상태 전이 시 422 반환")
    void updateShippingStatus_invalidTransition_returns422() throws Exception {
        given(shippingCommandService.updateStatus(any()))
                .willThrow(new InvalidStatusTransitionException(ShippingStatus.PREPARING, ShippingStatus.DELIVERED));

        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELIVERED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("SHIPPED 전이 시 trackingNumber 누락이면 400 반환")
    void updateShippingStatus_shippedWithoutTracking_returns400() throws Exception {
        given(shippingCommandService.updateStatus(any()))
                .willThrow(new InvalidShippingException("Tracking number is required when status is SHIPPED"));

        mockMvc.perform(put("/api/shippings/ship-1/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHIPPING_REQUEST"));
    }

    // ─── GET /api/shippings (Admin) ──────────────────────────────────

    @Test
    @DisplayName("관리자 배송 목록 조회 성공 시 200 반환")
    void listShippings_admin_returns200() throws Exception {
        ShippingSummary summary = new ShippingSummary(
                "ship-1", "order-1", ShippingStatus.PREPARING, null, null, true, NOW, NOW);
        PageResult<ShippingSummary> pageResult = new PageResult<>(List.of(summary), 0, 20, 1, 1);
        given(shippingQueryService.listShippings(any(), any(), any())).willReturn(pageResult);

        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].shippingId").value("ship-1"))
                .andExpect(jsonPath("$.content[0].wmsRouted").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("비관리자가 배송 목록 조회 시 403 반환")
    void listShippings_nonAdmin_returns403() throws Exception {
        given(shippingQueryService.listShippings(any(), any(), any()))
                .willThrow(new AccessDeniedException("Admin role required"));

        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("X-User-Role 누락 시 401 반환")
    void listShippings_missingUserRole_returns401() throws Exception {
        mockMvc.perform(get("/api/shippings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("상태 필터로 배송 목록 조회 성공")
    void listShippings_withStatusFilter_returns200() throws Exception {
        PageResult<ShippingSummary> pageResult = new PageResult<>(List.of(), 0, 20, 0, 0);
        given(shippingQueryService.listShippings(any(), eq(ShippingStatus.SHIPPED), any())).willReturn(pageResult);

        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("유효하지 않은 status 필터 시 400 반환")
    void listShippings_invalidStatusFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHIPPING_REQUEST"));
    }

    @Test
    @DisplayName("size가 0이면 기본값 20 사용")
    void listShippings_sizeZero_defaultUsed() throws Exception {
        PageResult<ShippingSummary> pageResult = new PageResult<>(List.of(), 0, 20, 0, 0);
        given(shippingQueryService.listShippings(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("size가 최대값(100) 초과 시 100으로 클램핑")
    void listShippings_sizeExceedsMax_clampedTo100() throws Exception {
        given(shippingQueryService.listShippings(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("page가 음수이면 0으로 대체")
    void listShippings_pageNegative_replacedWithZero() throws Exception {
        given(shippingQueryService.listShippings(any(), any(), any()))
                .willAnswer(inv -> {
                    PageQuery pq = inv.getArgument(2, PageQuery.class);
                    return new PageResult<>(List.of(), pq.page(), pq.size(), 0L, 0);
                });

        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }
}
