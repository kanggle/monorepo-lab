package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.config.SecurityConfig;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.SnapshotId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the internal, network-trusted replenishment-batch endpoint
 * (ADR-MONO-027 §D7.1). It is {@code permitAll} (the unattended sweep has no
 * operator token) and returns the cross-tenant snapshot.
 */
@WebMvcTest(controllers = InternalSnapshotController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InternalSnapshotControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InventoryVisibilityApplicationService applicationService;

    private final UUID nodeId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-06-01T10:00:00Z");

    private InventorySnapshot snapshot(String sku, BigDecimal qty) {
        return new InventorySnapshot(
                SnapshotId.of(UUID.randomUUID()),
                NodeId.of(nodeId),
                Sku.of(sku),
                "globex-corp",
                Quantity.of(qty),
                UUID.randomUUID(),
                now,
                0,
                now);
    }

    @Test
    void internalSnapshot_withoutAuth_returns200_permitAll() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenants()).thenReturn(List.of());

        // No Authorization header — the internal endpoint is permitAll.
        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(0));
    }

    @Test
    void internalSnapshot_returnsCrossTenantRows_mapped() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenants())
                .thenReturn(List.of(snapshot("SKU-001", new BigDecimal("7"))));

        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data[0].nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.data[0].availableQty").value(7));
    }
}
