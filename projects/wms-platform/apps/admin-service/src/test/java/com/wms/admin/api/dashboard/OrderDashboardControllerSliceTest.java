package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.readmodel.outbound.OrderSummaryEntity;
import com.wms.admin.readmodel.outbound.OrderSummaryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrderDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OrderDashboardControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OrderSummaryRepository repository;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    private OrderSummaryEntity sample() {
        return new OrderSummaryEntity(UUID.randomUUID(), "ORD-1", UUID.randomUUID(),
                null, null, "RECEIVED", "WEBHOOK_ERP", LocalDate.of(2026, 5, 12), 1, null,
                NOW, null, NOW);
    }

    @Test
    void list_viewer_returns200() throws Exception {
        Page<OrderSummaryEntity> page = new PageImpl<>(List.of(sample()),
                PageRequest.of(0, 20), 1);
        when(repository.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/orders")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNo").value("ORD-1"));
    }

    @Test
    void list_dateRangeFilter_passedToRepository() throws Exception {
        Page<OrderSummaryEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(repository.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/orders")
                        .param("requiredShipDateFrom", "2026-05-01")
                        .param("requiredShipDateTo", "2026-05-09")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk());

        verify(repository).search(any(), any(), any(), any(),
                eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 5, 9)), any());
    }

    @Test
    void list_dateRangeInverted_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/orders")
                        .param("requiredShipDateFrom", "2026-05-09")
                        .param("requiredShipDateTo", "2026-05-01")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // A role OUTSIDE the WMS hierarchy (SUPERADMIN>ADMIN>OPERATOR>VIEWER) cannot satisfy
    // hasRole('WMS_VIEWER'); WMS_OPERATOR would be allowed (it is above VIEWER), so a
    // non-hierarchy role is used to prove the deny path.
    @Test
    void list_roleOutsideWmsHierarchy_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/orders")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_GUEST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/orders"))
                .andExpect(status().isUnauthorized());
    }
}
