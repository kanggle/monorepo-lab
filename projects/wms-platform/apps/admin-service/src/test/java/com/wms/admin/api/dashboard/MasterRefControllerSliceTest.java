package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
class MasterRefControllerSliceTest {

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
        when(warehouseRepo.search(isNull(), isNull(), any(Pageable.class))).thenReturn(page);

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

    // --- TASK-BE-630: § 1.7 filters reach the query ---------------------------------
    // 🔴 The defect was silent: q/status were sent and dropped, the page came back
    //    unfiltered with 200. So these assert what the REPOSITORY received, not the status.

    private static org.springframework.test.web.servlet.request.RequestPostProcessor viewer() {
        return jwt().jwt(j -> j.claim("tenant_id", "wms"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"));
    }

    @Test
    @DisplayName("q + status reach the warehouse search as an escaped lower-case pattern")
    void list_qAndStatus_arePassedToSearch() throws Exception {
        when(warehouseRepo.search(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/dashboard/refs/warehouses")
                        .param("q", " WH_1 ").param("status", "ACTIVE").with(viewer()))
                .andExpect(status().isOk());

        verify(warehouseRepo).search(eq("%wh\\_1%"), eq("ACTIVE"), any(Pageable.class));
    }

    @Test
    @DisplayName("warehouseId scopes locations; blank q is no filter")
    void list_locations_warehouseIdIsPassed() throws Exception {
        UUID wh = UUID.randomUUID();
        when(locationRepo.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/dashboard/refs/locations")
                        .param("q", "   ").param("warehouseId", wh.toString()).with(viewer()))
                .andExpect(status().isOk());

        verify(locationRepo).search(isNull(), isNull(), eq(wh), any(Pageable.class));
    }

    @Test
    @DisplayName("skuId scopes lots")
    void list_lots_skuIdIsPassed() throws Exception {
        UUID sku = UUID.randomUUID();
        when(lotRepo.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/dashboard/refs/lots")
                        .param("q", "L-2026").param("skuId", sku.toString()).with(viewer()))
                .andExpect(status().isOk());

        verify(lotRepo).search(eq("%l-2026%"), isNull(), eq(sku), any(Pageable.class));
    }

    @Test
    @DisplayName("repeated partnerType filters partners; absent = anyType")
    void list_partners_partnerTypeIsPassed() throws Exception {
        when(partnerRepo.search(any(), any(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/dashboard/refs/partners")
                        .param("partnerType", "SUPPLIER").param("partnerType", "BOTH").with(viewer()))
                .andExpect(status().isOk());
        verify(partnerRepo).search(isNull(), isNull(), eq(false),
                eq(List.of("SUPPLIER", "BOTH")), any(Pageable.class));

        mockMvc.perform(get("/api/v1/admin/dashboard/refs/partners").with(viewer()))
                .andExpect(status().isOk());
        verify(partnerRepo).search(isNull(), isNull(), eq(true), eq(List.of("")), any(Pageable.class));
    }

    @Test
    @DisplayName("a filter that does not apply to the type is ignored, not rejected")
    void list_inapplicableFilter_isIgnored() throws Exception {
        when(skuRepo.search(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/dashboard/refs/skus")
                        .param("warehouseId", UUID.randomUUID().toString()).with(viewer()))
                .andExpect(status().isOk());

        verify(skuRepo).search(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("malformed warehouseId -> 400 VALIDATION_ERROR (contract § 1.7)")
    void list_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/refs/locations")
                        .param("warehouseId", "WH01").with(viewer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(locationRepo);
    }
}
