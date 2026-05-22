package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.cache.SnapshotAggregationCache;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.config.SecurityConfig;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {InventoryVisibilityController.class, NodeStalenessController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InventoryVisibilityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InventoryVisibilityApplicationService applicationService;

    @MockitoBean
    SnapshotAggregationCache cache;

    private final NodeId nodeId = NodeId.of(UUID.randomUUID());
    private final Instant now = Instant.parse("2026-05-01T10:00:00Z");

    @Test
    void getSnapshot_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/inventory-visibility/snapshot"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSnapshot_withValidJwt_returns200() throws Exception {
        InventorySnapshot snap = InventorySnapshot.create(
                nodeId, Sku.of("SKU-001"), "scm",
                Quantity.of(100), UUID.randomUUID(), now);

        when(applicationService.getCrossNodeSnapshot(eq("scm"), anyInt(), anyInt()))
                .thenReturn(List.of(snap));
        when(applicationService.countCrossNodeSnapshot(eq("scm"))).thenReturn(1L);
        when(applicationService.getStaleness(eq("scm"))).thenReturn(List.of());

        mockMvc.perform(get("/api/inventory-visibility/snapshot")
                        .with(validJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.meta.warning").value("Not for procurement decisions (S5)"));
    }

    @Test
    void getSnapshot_byNodeId_returns200() throws Exception {
        InventorySnapshot snap = InventorySnapshot.create(
                nodeId, Sku.of("SKU-001"), "scm",
                Quantity.of(50), UUID.randomUUID(), now);

        when(applicationService.getSnapshotByNode(eq(nodeId.toString()), eq("scm")))
                .thenReturn(List.of(snap));
        when(applicationService.getStaleness(eq("scm"))).thenReturn(List.of());

        mockMvc.perform(get("/api/inventory-visibility/snapshot")
                        .param("nodeId", nodeId.toString())
                        .with(validJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getStaleness_withValidJwt_returns200() throws Exception {
        NodeStaleness staleness = NodeStaleness.create(nodeId, "scm", now, UUID.randomUUID());

        when(applicationService.getStaleness(eq("scm"))).thenReturn(List.of(staleness));

        mockMvc.perform(get("/api/inventory-visibility/staleness")
                        .with(validJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.data[0].stalenessStatus").value("FRESH"));
    }

    @Test
    void getNodes_withValidJwt_returns200() throws Exception {
        when(applicationService.getNodes(eq("scm"))).thenReturn(List.of());

        mockMvc.perform(get("/api/inventory-visibility/nodes")
                        .with(validJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * JWT with tenant_id=scm — bypasses full OAuth2 validation in slice test.
     */
    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject("test-account-id")
                        .claim("tenant_id", "scm")
                        .claim("roles", List.of("OPERATOR")));
    }
}
