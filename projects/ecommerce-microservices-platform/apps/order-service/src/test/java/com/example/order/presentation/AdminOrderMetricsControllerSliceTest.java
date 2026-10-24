package com.example.order.presentation;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.MetricsPeriod;
import com.example.order.TestOrderServiceApplication;
import com.example.order.application.dto.OrderInsights;
import com.example.order.application.service.AdminOrderStatusService;
import com.example.order.application.service.OrderQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/admin/orders/metrics} and the {@code period} of {@code /insights}
 * (ADR-MONO-084 P2, TASK-MONO-807): the parameter contract and the wire shape of
 * {@code platform/contracts/performance-metrics-contract.md}. Authorization per persona is
 * {@link AdminOrderBusinessRoleGateSliceTest}.
 */
@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = TestOrderServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminOrderController — /metrics · /insights?period 슬라이스 테스트")
class AdminOrderMetricsControllerSliceTest {

    private static final String READ_ROLE = "ECOMMERCE_OPERATOR,ECOMMERCE_ORDER_READ";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private AdminOrderStatusService adminOrderStatusService;

    @Test
    @DisplayName("period=month → 200, 공용 계약 모양(값은 정수 문자열)")
    void metrics_month_returnsContractShape() throws Exception {
        KstPeriodBounds bounds = KstPeriodBounds.from(
                Clock.fixed(Instant.parse("2026-10-23T05:05:00Z"), ZoneId.of("UTC")));
        given(orderQueryService.getMetrics(MetricsPeriod.MONTH)).willReturn(PerformanceMetrics.of(
                bounds, MetricsPeriod.MONTH,
                List.of(PerformanceMetrics.Metric.money("revenue", "KRW", 12_340_000, 11_020_000),
                        PerformanceMetrics.Metric.count("units", 812, 790),
                        PerformanceMetrics.Metric.count("orders", 301, 288)),
                Map.of()));

        mockMvc.perform(get("/api/admin/orders/metrics").param("period", "month").header("X-User-Role", READ_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("month"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.current.from").value("2026-10-01T00:00:00+09:00"))
                .andExpect(jsonPath("$.previous.to").value("2026-09-23T14:05:00+09:00"))
                .andExpect(jsonPath("$.metrics[0].key").value("revenue"))
                .andExpect(jsonPath("$.metrics[0].currency").value("KRW"))
                // a string, not a JSON number — the console's money rule (F5)
                .andExpect(jsonPath("$.metrics[0].current").value("12340000"))
                .andExpect(jsonPath("$.metrics[0].current").isString())
                .andExpect(jsonPath("$.metrics[1].currency").isEmpty())
                .andExpect(jsonPath("$.series.length()").value(23))
                .andExpect(jsonPath("$.series[0].values.revenue").value("0"));
    }

    @Test
    @DisplayName("🔴 TASK-MONO-817: 와일드카드 테넌트(*) → 403, 0 으로 답하지 않고 서비스도 부르지 않는다 · 대조군 구체 테넌트는 200")
    void metrics_wildcardTenant_403_concreteTenant_200() throws Exception {
        mockMvc.perform(get("/api/admin/orders/metrics").param("period", "week")
                        .header("X-User-Role", "").header("X-Tenant-Id", "*"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders/metrics").param("period", "week")
                        .header("X-User-Role", READ_ROLE).header("X-Tenant-Id", "*"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderQueryService);

        KstPeriodBounds bounds = KstPeriodBounds.from(
                Clock.fixed(Instant.parse("2026-10-23T05:05:00Z"), ZoneId.of("UTC")));
        given(orderQueryService.getMetrics(MetricsPeriod.WEEK)).willReturn(PerformanceMetrics.of(
                bounds, MetricsPeriod.WEEK, List.of(PerformanceMetrics.Metric.count("orders", 3, 1)), Map.of()));
        mockMvc.perform(get("/api/admin/orders/metrics").param("period", "week")
                        .header("X-User-Role", READ_ROLE).header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics[0].current").value("3"));
    }

    @Test
    @DisplayName("period 없음 · 모르는 값 → 400 INVALID_ORDER_REQUEST, 서비스는 부르지 않는다")
    void metrics_badPeriod_400() throws Exception {
        mockMvc.perform(get("/api/admin/orders/metrics").header("X-User-Role", READ_ROLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
        mockMvc.perform(get("/api/admin/orders/metrics").param("period", "today").header("X-User-Role", READ_ROLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("/insights 는 period 를 선택으로 받는다 — 없으면 전체, 있으면 그 기간, 모르는 값은 400")
    void insights_optionalPeriod() throws Exception {
        OrderInsights empty = new OrderInsights(List.of(), List.of(), List.of(), List.of());
        given(orderQueryService.getInsights(Optional.empty())).willReturn(empty);
        given(orderQueryService.getInsights(Optional.of(MetricsPeriod.WEEK))).willReturn(empty);

        mockMvc.perform(get("/api/admin/orders/insights").header("X-User-Role", READ_ROLE))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/orders/insights").param("period", "WEEK").header("X-User-Role", READ_ROLE))
                .andExpect(status().isOk());
        verify(orderQueryService).getInsights(Optional.empty());
        verify(orderQueryService).getInsights(Optional.of(MetricsPeriod.WEEK));

        mockMvc.perform(get("/api/admin/orders/insights").param("period", "year").header("X-User-Role", READ_ROLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
    }
}
