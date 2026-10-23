package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.application.dashboard.WarehouseMetricsService;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.infra.security.SecurityContextReadScopeProvider;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.PutawayFactRepository;
import com.wms.admin.readmodel.throughput.TimedQuantity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TASK-MONO-808 — {@code GET /api/v1/admin/dashboard/metrics}. The real
 * {@link WarehouseMetricsService} runs on a fixed clock; only the repositories are mocks,
 * so the windows the controller asks for are the ones production would ask for.
 */
@WebMvcTest(controllers = MetricsDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        SecurityContextReadScopeProvider.class, WarehouseMetricsService.class,
        MetricsDashboardControllerSliceTest.FixedClock.class})
class MetricsDashboardControllerSliceTest {

    /** 2026-10-23 (Fri) 14:00 KST. Week starts Mon 10-19 00:00 KST = 10-18T15:00Z. */
    static final Instant NOW = Instant.parse("2026-10-23T05:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean PutawayFactRepository putawayFactRepo;
    @MockitoBean ShipmentSummaryRepository shipmentRepo;

    private static RequestPostProcessor viewer(String tenant) {
        return jwt().jwt(j -> j.claim("tenant_id", tenant))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"));
    }

    @Test
    void week_returnsSharedShape_withKstWindowsAndZeroFilledSeries() throws Exception {
        Instant weekStart = Instant.parse("2026-10-18T15:00:00Z");
        Instant prevStart = Instant.parse("2026-10-11T15:00:00Z");
        Instant prevEnd = Instant.parse("2026-10-16T05:00:00Z");
        when(putawayFactRepo.findInWindow(isNull(), eq(weekStart), eq(NOW))).thenReturn(List.of(
                new TimedQuantity(Instant.parse("2026-10-19T15:30:00Z"), 40), // 10-20 00:30 KST
                new TimedQuantity(Instant.parse("2026-10-20T03:00:00Z"), 60))); // 10-20 12:00 KST
        when(putawayFactRepo.findInWindow(isNull(), eq(prevStart), eq(prevEnd)))
                .thenReturn(List.of(new TimedQuantity(prevStart, 80)));
        when(shipmentRepo.findShippedInWindow(isNull(), eq(weekStart), eq(NOW))).thenReturn(List.of(
                new TimedQuantity(Instant.parse("2026-10-22T01:00:00Z"), 5),
                new TimedQuantity(Instant.parse("2026-10-22T02:00:00Z"), 7)));
        when(shipmentRepo.findShippedInWindow(isNull(), eq(prevStart), eq(prevEnd)))
                .thenReturn(List.of(new TimedQuantity(prevEnd.minusSeconds(1), 9)));

        mockMvc.perform(get("/api/v1/admin/dashboard/metrics").param("period", "week").with(viewer("wms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("week"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.current.from").value("2026-10-19T00:00:00+09:00"))
                .andExpect(jsonPath("$.current.to").value("2026-10-23T14:00:00+09:00"))
                .andExpect(jsonPath("$.previous.from").value("2026-10-12T00:00:00+09:00"))
                .andExpect(jsonPath("$.previous.to").value("2026-10-16T14:00:00+09:00"))
                .andExpect(jsonPath("$.metrics[0].key").value("qtyReceived"))
                .andExpect(jsonPath("$.metrics[0].unit").value("count"))
                .andExpect(jsonPath("$.metrics[0].current").value("100"))
                .andExpect(jsonPath("$.metrics[0].previous").value("80"))
                .andExpect(jsonPath("$.metrics[1].key").value("qtyShipped"))
                .andExpect(jsonPath("$.metrics[1].current").value("12"))
                .andExpect(jsonPath("$.metrics[1].previous").value("9"))
                .andExpect(jsonPath("$.metrics[2].key").value("shipmentCount"))
                .andExpect(jsonPath("$.metrics[2].current").value("2"))
                .andExpect(jsonPath("$.metrics[2].previous").value("1"))
                // Mon..Fri, zero-filled; both putaways on the KST 10-20 (one is UTC 10-19)
                .andExpect(jsonPath("$.series.length()").value(5))
                .andExpect(jsonPath("$.series[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.series[0].values.qtyReceived").value("0"))
                .andExpect(jsonPath("$.series[1].date").value("2026-10-20"))
                .andExpect(jsonPath("$.series[1].values.qtyReceived").value("100"))
                .andExpect(jsonPath("$.series[3].values.shipmentCount").value("2"))
                .andExpect(jsonPath("$.series[4].values.qtyShipped").value("0"));
    }

    @Test
    void warehouseId_isPassedThrough() throws Exception {
        UUID wh = UUID.randomUUID();
        when(putawayFactRepo.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(shipmentRepo.findShippedInWindow(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/dashboard/metrics")
                        .param("period", "month").param("warehouseId", wh.toString()).with(viewer("wms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("month"));

        verify(putawayFactRepo, times(2)).findInWindow(eq(wh), any(), any());
        verify(shipmentRepo, times(2)).findShippedInWindow(eq(wh), any(), any());
    }

    /**
     * AC-3 «테넌트 격리» — closed against contract § 1.0: these metrics are the
     * warehouse-global class (like § 1.2 throughput), so isolation here means the tenant
     * claim must NOT partition them. Two tenants, same rows, same body.
     */
    @Test
    void tenantClaim_doesNotPartition_warehouseGlobal() throws Exception {
        when(putawayFactRepo.findInWindow(any(), any(), any()))
                .thenReturn(List.of(new TimedQuantity(NOW.minusSeconds(60), 3)));
        when(shipmentRepo.findShippedInWindow(any(), any(), any())).thenReturn(List.of());

        String a = mockMvc.perform(get("/api/v1/admin/dashboard/metrics").param("period", "week")
                        .with(viewer("demo-corp")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/v1/admin/dashboard/metrics").param("period", "week")
                        .with(viewer("ecommerce")))
                .andExpect(status().isOk())
                .andExpect(content().json(a, true));
    }

    @Test
    void missingPeriod_is400ValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/metrics").with(viewer("wms")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(putawayFactRepo, never()).findInWindow(any(), any(), any());
    }

    @Test
    void unknownPeriod_is400ValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/metrics").param("period", "year").with(viewer("wms")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void withoutViewerRole_is403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/metrics").param("period", "week")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_is401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/metrics").param("period", "week"))
                .andExpect(status().isUnauthorized());
    }
}
