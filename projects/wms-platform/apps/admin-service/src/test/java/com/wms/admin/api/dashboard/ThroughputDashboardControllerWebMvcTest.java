package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyEntity;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyEntity;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice coverage for {@link ThroughputDashboardController} — previously had no slice test.
 * Covers TASK-BE-525 AC-3 (positive VIEWER-200 + 403 wrong-role + 401 unauth).
 */
@WebMvcTest(controllers = ThroughputDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ThroughputDashboardControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ThroughputInboundDailyRepository inboundRepo;
    @MockitoBean ThroughputOutboundDailyRepository outboundRepo;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    @DisplayName("VIEWER gets aggregated throughput -> 200")
    void get_viewer_returns200() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 5, 5);
        when(inboundRepo.findByWarehouseIdAndDateBetweenOrderByDateAsc(eq(warehouseId), any(), any()))
                .thenReturn(List.of(new ThroughputInboundDailyEntity(day, warehouseId, 5, 50, NOW)));
        when(outboundRepo.findByWarehouseIdAndDateBetweenOrderByDateAsc(eq(warehouseId), any(), any()))
                .thenReturn(List.of(new ThroughputOutboundDailyEntity(day, warehouseId, 3, 30, NOW)));

        mockMvc.perform(get("/api/v1/admin/dashboard/throughput")
                        .param("warehouseId", warehouseId.toString())
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-09")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.inbound.putawayCount").value(5))
                .andExpect(jsonPath("$.totals.inbound.qtyReceived").value(50))
                .andExpect(jsonPath("$.totals.outbound.shipmentCount").value(3))
                .andExpect(jsonPath("$.totals.outbound.qtyShipped").value(30));
    }

    // A role OUTSIDE the WMS hierarchy (SUPERADMIN>ADMIN>OPERATOR>VIEWER) cannot satisfy
    // hasRole('WMS_VIEWER'); WMS_OPERATOR would be allowed (it is above VIEWER), so a
    // non-hierarchy role is used to prove the deny path.
    @Test
    @DisplayName("role outside the WMS hierarchy -> 403")
    void get_roleOutsideWmsHierarchy_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/throughput")
                        .param("warehouseId", UUID.randomUUID().toString())
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-09")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_GUEST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("unauthenticated -> 401")
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/throughput")
                        .param("warehouseId", UUID.randomUUID().toString())
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-09"))
                .andExpect(status().isUnauthorized());
    }
}
