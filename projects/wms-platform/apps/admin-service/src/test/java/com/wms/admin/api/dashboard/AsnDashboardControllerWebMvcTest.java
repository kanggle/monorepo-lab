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
import com.wms.admin.readmodel.inbound.AsnSummaryEntity;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inbound.InspectionSummaryRepository;
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

@WebMvcTest(controllers = AsnDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AsnDashboardControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AsnSummaryRepository asnRepo;
    @MockitoBean InspectionSummaryRepository inspectionRepo;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    private AsnSummaryEntity sample() {
        return new AsnSummaryEntity(UUID.randomUUID(), "ASN-1", UUID.randomUUID(),
                null, null, "CREATED", "MANUAL", LocalDate.of(2026, 5, 12), 1, NOW, null, NOW);
    }

    @Test
    void list_viewer_returns200() throws Exception {
        Page<AsnSummaryEntity> page = new PageImpl<>(List.of(sample()),
                PageRequest.of(0, 20), 1);
        when(asnRepo.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/asns")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].asnNo").value("ASN-1"));
    }

    @Test
    void list_dateRangeFilter_passedToRepository() throws Exception {
        Page<AsnSummaryEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(asnRepo.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/asns")
                        .param("expectedArriveDateFrom", "2026-05-10")
                        .param("expectedArriveDateTo", "2026-05-15")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk());

        verify(asnRepo).search(any(), any(), any(), any(),
                eq(LocalDate.of(2026, 5, 10)), eq(LocalDate.of(2026, 5, 15)), any());
    }

    @Test
    void list_dateRangeInverted_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/asns")
                        .param("expectedArriveDateFrom", "2026-05-15")
                        .param("expectedArriveDateTo", "2026-05-10")
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
        mockMvc.perform(get("/api/v1/admin/dashboard/asns")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_GUEST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/asns"))
                .andExpect(status().isUnauthorized());
    }
}
