package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.page.PageResult;
import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.QueryOrderUseCase;
import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.port.in.QuerySagaUseCase;
import com.wms.outbound.application.result.OrderSummaryResult;
import com.wms.outbound.config.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for {@link OrderQueryController#listOrders} (ADR-MONO-058 §D3,
 * TASK-BE-568) — proves the {@code com.example.common.page.PageResult}-backed
 * response carries {@code totalPages} and the effective {@code page}/{@code size}
 * echoed back, per {@code outbound-service-api.md} §1.3 / §Pagination.
 */
@WebMvcTest(controllers = OrderQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class OrderQueryListControllerSliceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryOrderUseCase queryOrder;

    @MockitoBean
    QueryPickingRequestUseCase queryPickingRequest;

    @MockitoBean
    QuerySagaUseCase querySaga;

    @Test
    @DisplayName("§1.3 multi-page fixture → totalPages + echoed page/size present and correct")
    @WithMockUser(roles = "OUTBOUND_READ")
    void listOrders_multiPageFixture_returnsTotalPagesAndEchoedPageSize() throws Exception {
        OrderSummaryResult summary = new OrderSummaryResult(
                UUID.randomUUID(), "ORD-20260429-9001", "MANUAL",
                UUID.randomUUID(), UUID.randomUUID(), "PICKING", "REQUESTED",
                1, 50L, LocalDate.of(2026, 5, 2), T0, T0);
        // 25 total elements at size=10 → 3 pages (AC-2 fixture).
        when(queryOrder.list(any())).thenReturn(
                new PageResult<>(List.of(summary), 0, 10, 25L, 3));

        mockMvc.perform(get("/api/v1/outbound/orders").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNo").value("ORD-20260429-9001"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(25))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    @DisplayName("§Pagination size > 100 → 400 VALIDATION_ERROR (AC-8)")
    @WithMockUser(roles = "OUTBOUND_READ")
    void listOrders_sizeAboveMax_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/outbound/orders").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("unauthenticated request → 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outbound/orders"))
                .andExpect(status().isUnauthorized());
    }
}
