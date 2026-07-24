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
class InternalSnapshotControllerSliceTest {

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

    private InventoryVisibilityApplicationService.SnapshotWithNodeMeta row(
            String sku, BigDecimal qty, String warehouseCode) {
        return row(sku, qty, warehouseCode, "WMS_WAREHOUSE");
    }

    private InventoryVisibilityApplicationService.SnapshotWithNodeMeta row(
            String sku, BigDecimal qty, String warehouseCode, String nodeType) {
        return new InventoryVisibilityApplicationService.SnapshotWithNodeMeta(
                snapshot(sku, qty), warehouseCode, nodeType);
    }

    @Test
    void internalSnapshot_withoutAuth_returns200_permitAll() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenantsWithWarehouseCode())
                .thenReturn(List.of());

        // No Authorization header — the internal endpoint is permitAll.
        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(0));
    }

    @Test
    void internalSnapshot_returnsCrossTenantRows_mapped() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenantsWithWarehouseCode())
                .thenReturn(List.of(row("SKU-001", new BigDecimal("7"), "WH01")));

        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data[0].nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.data[0].availableQty").value(7))
                .andExpect(jsonPath("$.data[0].nodeType").value("WMS_WAREHOUSE"));
    }

    /**
     * ADR-MONO-055 §D2/§D3 (TASK-SCM-BE-048): the internal read surface must serve the node's
     * type so demand-planning's batch sweep can widen its replenishment target beyond wms
     * warehouses — a THIRD_PARTY_LOGISTICS node drives a PO addressed to that 3PL node.
     */
    @Test
    void internalSnapshot_servesNodeType_including3pl() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenantsWithWarehouseCode())
                .thenReturn(List.of(row("SKU-3PL", new BigDecimal("2"), null, "THIRD_PARTY_LOGISTICS")));

        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nodeType").value("THIRD_PARTY_LOGISTICS"))
                .andExpect(jsonPath("$.data[0].warehouseCode").doesNotExist());
    }

    /**
     * The node type is nullable (a node absent from the registry). A null must still serve
     * the row — the caller reads absent/null as WMS_WAREHOUSE (backward compat).
     */
    @Test
    void internalSnapshot_nullNodeType_rowStillServed() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenantsWithWarehouseCode())
                .thenReturn(List.of(row("SKU-001", new BigDecimal("7"), "WH01", null)));

        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(1))
                .andExpect(jsonPath("$.data[0].nodeType").doesNotExist());
    }

    /**
     * ADR-MONO-050 D9 / TASK-SCM-BE-037: the internal read surface must serve the node's
     * warehouse CODE — it is the field demand-planning's batch sweep threads onto the
     * suggestion so a batch-origin PO can address a wms inbound-expected.
     */
    @Test
    void internalSnapshot_servesWarehouseCode() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenantsWithWarehouseCode())
                .thenReturn(List.of(row("SKU-001", new BigDecimal("7"), "WH01")));

        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].warehouseCode").value("WH01"));
    }

    /**
     * The code is best-effort in wms, so a node may not have learned one. The row must
     * still be served (with a null code) — a null must never drop the sweep candidate.
     */
    @Test
    void internalSnapshot_nullWarehouseCode_rowStillServed() throws Exception {
        when(applicationService.getAllSnapshotsAcrossTenantsWithWarehouseCode())
                .thenReturn(List.of(row("SKU-001", new BigDecimal("7"), null)));

        mockMvc.perform(get("/internal/inventory-visibility/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data[0].warehouseCode").doesNotExist());
    }
}
