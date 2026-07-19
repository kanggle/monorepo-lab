package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefEntity;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice coverage for {@link MasterRefController} — previously had no slice test.
 * Covers TASK-BE-525 AC-3 (positive VIEWER-200 + 403 wrong-role + 401 unauth).
 */
@WebMvcTest(controllers = MasterRefController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MasterRefControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean WarehouseRefRepository warehouseRepo;
    @MockitoBean ZoneRefRepository zoneRepo;
    @MockitoBean LocationRefRepository locationRepo;
    @MockitoBean SkuRefRepository skuRepo;
    @MockitoBean LotRefRepository lotRepo;
    @MockitoBean PartnerRefRepository partnerRepo;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    @DisplayName("VIEWER can list warehouses -> 200")
    void list_viewer_returns200() throws Exception {
        WarehouseRefEntity e = new WarehouseRefEntity(UUID.randomUUID(), "WH-1", "Main Warehouse",
                "UTC", "ACTIVE", NOW);
        Page<WarehouseRefEntity> page = new PageImpl<>(List.of(e), PageRequest.of(0, 20), 1);
        when(warehouseRepo.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/refs/warehouses")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("WH-1"))
                .andExpect(jsonPath("$.content[0].name").value("Main Warehouse"));
    }

    // A role OUTSIDE the WMS hierarchy (SUPERADMIN>ADMIN>OPERATOR>VIEWER) cannot satisfy
    // hasRole('WMS_VIEWER'); WMS_OPERATOR would be allowed (it is above VIEWER), so a
    // non-hierarchy role is used to prove the deny path.
    @Test
    @DisplayName("role outside the WMS hierarchy -> 403")
    void list_roleOutsideWmsHierarchy_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/refs/warehouses")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_GUEST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("unauthenticated -> 401")
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/refs/warehouses"))
                .andExpect(status().isUnauthorized());
    }
}
