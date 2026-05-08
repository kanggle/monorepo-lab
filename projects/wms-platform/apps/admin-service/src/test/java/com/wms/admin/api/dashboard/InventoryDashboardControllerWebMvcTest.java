package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.config.SecurityConfig;
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

@WebMvcTest(controllers = InventoryDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InventoryDashboardControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean InventorySnapshotRepository repository;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    private InventorySnapshotEntity sample() {
        return new InventorySnapshotEntity(UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), "WH01-A-01", "SKU-1", null,
                100, 0, 0, false, NOW, NOW);
    }

    @Test
    void list_viewer_returns200() throws Exception {
        Page<InventorySnapshotEntity> page = new PageImpl<>(List.of(sample()),
                PageRequest.of(0, 20), 1);
        when(repository.search(any(), any(), any(), any(), anyBoolean(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/dashboard/inventory")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].onHandQty").value(100));
    }

    @Test
    void byKey_unauthenticated_returns401() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/dashboard/inventory/by-key")
                        .param("locationId", location.toString())
                        .param("skuId", sku.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void byKey_notFound_returns404() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(repository.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/dashboard/inventory/by-key")
                        .param("locationId", location.toString())
                        .param("skuId", sku.toString())
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isNotFound());
    }
}
