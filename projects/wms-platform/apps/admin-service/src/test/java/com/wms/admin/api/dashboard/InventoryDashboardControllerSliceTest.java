package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.application.repository.SettingRepository;
import com.wms.admin.application.settings.LowStockThresholdReader;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import com.wms.admin.readmodel.inventory.InventorySnapshotId;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The real {@link LowStockThresholdReader} is imported and only the setting
 * store is mocked, so these cells exercise the whole TASK-BE-644 path inside
 * the slice: setting value → threshold → repository filter argument → derived
 * {@code lowStockFlag} with strict {@code <}.
 */
@WebMvcTest(controllers = InventoryDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, LowStockThresholdReader.class})
class InventoryDashboardControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean InventorySnapshotRepository repository;
    @MockitoBean SettingRepository settingRepository;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");
    private static final String LIST = "/api/v1/admin/dashboard/inventory";

    private InventorySnapshotEntity row(int availableQty) {
        return new InventorySnapshotEntity(UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), null, "WH01-A-01", "SKU-1", null,
                availableQty, 0, 0, NOW, NOW);
    }

    private void settingValue(String valueJson) {
        Setting s = new Setting(LowStockThresholdReader.KEY, SettingScope.GLOBAL, null,
                valueJson, "{\"type\":\"integer\",\"minimum\":0,\"maximum\":100000}",
                "Default low-stock threshold quantity", 1, NOW, "seed", NOW, "ops");
        when(settingRepository.find(LowStockThresholdReader.KEY, null)).thenReturn(Optional.of(s));
    }

    private void pageOf(InventorySnapshotEntity... rows) {
        Page<InventorySnapshotEntity> page = new PageImpl<>(List.of(rows), PageRequest.of(0, 20),
                rows.length);
        when(repository.search(any(), any(), any(), any(), anyBoolean(), anyInt(), any(), any()))
                .thenReturn(page);
    }

    private static MockHttpServletRequestBuilder asViewer(MockHttpServletRequestBuilder b) {
        return b.with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER")));
    }

    @Test
    void list_viewer_returns200() throws Exception {
        pageOf(row(100));

        mockMvc.perform(asViewer(get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].onHandQty").value(100));
    }

    /** The setting value reaches the filter and the flag; equal-to-threshold is NOT low. */
    @Test
    void list_followsSetting_strictBoundary() throws Exception {
        settingValue("25");
        pageOf(row(24), row(25), row(26));

        mockMvc.perform(asViewer(get(LIST).param("lowStockOnly", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lowStockFlag").value(true))
                .andExpect(jsonPath("$.content[1].lowStockFlag").value(false))
                .andExpect(jsonPath("$.content[2].lowStockFlag").value(false));

        verify(repository).search(isNull(), isNull(), isNull(), isNull(), eq(true), eq(25),
                isNull(), any());
    }

    /** Above the old constant: 15 is low under a threshold of 25 (it was not under `<= 10`). */
    @Test
    void list_raisedSetting_flagsRowsTheOldConstantMissed() throws Exception {
        settingValue("25");
        pageOf(row(15));

        mockMvc.perform(asViewer(get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lowStockFlag").value(true));
    }

    @Test
    void list_settingMissing_fallsBackTo10() throws Exception {
        when(settingRepository.find(LowStockThresholdReader.KEY, null)).thenReturn(Optional.empty());
        pageOf(row(9), row(10));

        mockMvc.perform(asViewer(get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lowStockFlag").value(true))
                .andExpect(jsonPath("$.content[1].lowStockFlag").value(false));

        verify(repository).search(isNull(), isNull(), isNull(), isNull(), eq(false), eq(10),
                isNull(), any());
    }

    @Test
    void list_settingNotAnInteger_fallsBackTo10() throws Exception {
        settingValue("\"lots\"");
        pageOf(row(9));

        mockMvc.perform(asViewer(get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lowStockFlag").value(true));

        verify(repository).search(isNull(), isNull(), isNull(), isNull(), eq(false), eq(10),
                isNull(), any());
    }

    @Test
    void byKey_derivesFlagFromSetting() throws Exception {
        settingValue("30");
        when(repository.findById(any(InventorySnapshotId.class))).thenReturn(Optional.of(row(29)));

        mockMvc.perform(asViewer(get(LIST + "/by-key")
                        .param("locationId", UUID.randomUUID().toString())
                        .param("skuId", UUID.randomUUID().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockFlag").value(true));
    }

    @Test
    void byKey_unauthenticated_returns401() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();

        mockMvc.perform(get(LIST + "/by-key")
                        .param("locationId", location.toString())
                        .param("skuId", sku.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void byKey_notFound_returns404() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(repository.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());

        mockMvc.perform(asViewer(get(LIST + "/by-key")
                        .param("locationId", location.toString())
                        .param("skuId", sku.toString())))
                .andExpect(status().isNotFound());
    }
}
