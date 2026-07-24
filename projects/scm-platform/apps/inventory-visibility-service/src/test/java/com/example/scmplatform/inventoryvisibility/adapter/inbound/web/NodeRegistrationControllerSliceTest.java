package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService.RegisterThirdPartyLogisticsNodeResult;
import com.example.scmplatform.inventoryvisibility.config.SecurityConfig;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-SCM-BE-046 / TASK-SCM-BE-047 — {@code @WebMvcTest} slice for
 * {@link NodeRegistrationController}: node registration (201/200/409/422) and 3PL
 * observed-stock ingestion (200/404/409/422), plus fail-closed (401) with no bearer
 * token on both — same auth posture as the read-only {@link InventoryVisibilityController}
 * (reused {@link SecurityConfig}, no new auth surface).
 */
@WebMvcTest(controllers = NodeRegistrationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NodeRegistrationControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegisterThirdPartyLogisticsNodeService registrationService;

    @MockitoBean
    InventoryVisibilityApplicationService visibilityService;

    @MockitoBean
    ClockPort clock;

    private final Instant now = Instant.parse("2026-07-24T10:00:00Z");

    @Test
    void registerNode_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/inventory-visibility/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nodeExternalId": "3PL-EXT-1", "name": "품고 물류센터" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerNode_newExternalId_returns201() throws Exception {
        InventoryNode node = InventoryNode.registerThirdPartyLogistics(
                NodeId.of(UUID.randomUUID()), "scm", "3PL-EXT-1", "품고 물류센터", now);
        when(registrationService.register(eq("scm"), eq("3PL-EXT-1"), eq("품고 물류센터")))
                .thenReturn(new RegisterThirdPartyLogisticsNodeResult(node, true));

        mockMvc.perform(post("/api/inventory-visibility/nodes")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nodeExternalId": "3PL-EXT-1", "name": "품고 물류센터" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nodeType").value("THIRD_PARTY_LOGISTICS"))
                .andExpect(jsonPath("$.data.nodeExternalId").value("3PL-EXT-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void registerNode_repeatSameExternalId_returns200Idempotent() throws Exception {
        InventoryNode node = InventoryNode.registerThirdPartyLogistics(
                NodeId.of(UUID.randomUUID()), "scm", "3PL-EXT-1", "품고 물류센터", now);
        when(registrationService.register(eq("scm"), eq("3PL-EXT-1"), eq("품고 물류센터")))
                .thenReturn(new RegisterThirdPartyLogisticsNodeResult(node, false));

        mockMvc.perform(post("/api/inventory-visibility/nodes")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nodeExternalId": "3PL-EXT-1", "name": "품고 물류센터" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeType").value("THIRD_PARTY_LOGISTICS"));
    }

    @Test
    void registerNode_typeConflict_returns409() throws Exception {
        when(registrationService.register(eq("scm"), eq("WH-EXT-1"), eq("Not Really 3PL")))
                .thenThrow(new NodeTypeConflictException("WH-EXT-1",
                        NodeType.WMS_WAREHOUSE, NodeType.THIRD_PARTY_LOGISTICS));

        mockMvc.perform(post("/api/inventory-visibility/nodes")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nodeExternalId": "WH-EXT-1", "name": "Not Really 3PL" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NODE_TYPE_CONFLICT"));
    }

    @Test
    void registerNode_blankName_returns422() throws Exception {
        when(registrationService.register(eq("scm"), eq("3PL-EXT-1"), eq("")))
                .thenThrow(new IllegalArgumentException("name must not be blank"));

        mockMvc.perform(post("/api/inventory-visibility/nodes")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nodeExternalId": "3PL-EXT-1", "name": "" }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // TASK-SCM-BE-047 — POST /nodes/{nodeId}/observed-stock
    // -------------------------------------------------------------------------

    private static final String NODE_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void observeStock_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lines": [ { "skuCode": "SKU-001", "quantity": 10 } ] }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void observeStock_validRequest_returns200AndDefaultsObservedAtFromClock() throws Exception {
        when(clock.now()).thenReturn(now);

        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lines": [
                                    { "skuCode": "SKU-001", "quantity": 10 },
                                    { "skuCode": "SKU-002", "quantity": 0 }
                                  ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeId").value(NODE_ID))
                .andExpect(jsonPath("$.data.skuCount").value(2))
                .andExpect(jsonPath("$.data.observedAt").value(now.toString()));

        ArgumentCaptor<List<InventoryVisibilityApplicationService.ObservedLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(visibilityService).applyThirdPartyObservedStock(
                eq(NODE_ID), eq("scm"), eq(now), linesCaptor.capture());
        assertThat(linesCaptor.getValue()).hasSize(2);
        assertThat(linesCaptor.getValue().get(1).quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void observeStock_explicitObservedAt_isPassedThroughUnchanged() throws Exception {
        Instant explicit = Instant.parse("2026-06-01T00:00:00Z");

        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "observedAt": "2026-06-01T00:00:00Z",
                                  "lines": [ { "skuCode": "SKU-001", "quantity": 5 } ] }
                                """))
                .andExpect(status().isOk());

        verify(visibilityService).applyThirdPartyObservedStock(
                eq(NODE_ID), eq("scm"), eq(explicit), any());
    }

    @Test
    void observeStock_unknownNode_returns404() throws Exception {
        doThrow(new NodeNotFoundException(NODE_ID))
                .when(visibilityService)
                .applyThirdPartyObservedStock(eq(NODE_ID), anyString(), any(), any());
        when(clock.now()).thenReturn(now);

        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lines": [ { "skuCode": "SKU-001", "quantity": 10 } ] }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NODE_NOT_FOUND"));
    }

    @Test
    void observeStock_wrongTypeNode_returns409() throws Exception {
        doThrow(new NodeTypeConflictException("Inventory node nodeId=" + NODE_ID
                + " has type=WMS_WAREHOUSE; observed-stock ingestion requires THIRD_PARTY_LOGISTICS"))
                .when(visibilityService)
                .applyThirdPartyObservedStock(eq(NODE_ID), anyString(), any(), any());
        when(clock.now()).thenReturn(now);

        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lines": [ { "skuCode": "SKU-001", "quantity": 10 } ] }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NODE_TYPE_CONFLICT"));
    }

    @Test
    void observeStock_emptyLines_returns422() throws Exception {
        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lines": [] }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void observeStock_blankSkuCode_returns422() throws Exception {
        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lines": [ { "skuCode": "  ", "quantity": 10 } ] }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void observeStock_negativeQuantity_returns422() throws Exception {
        mockMvc.perform(post("/api/inventory-visibility/nodes/" + NODE_ID + "/observed-stock")
                        .with(validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "lines": [ { "skuCode": "SKU-001", "quantity": -1 } ] }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * JWT with tenant_id=scm — bypasses full OAuth2 validation in slice test
     * (matches {@code InventoryVisibilityControllerSliceTest}'s convention).
     */
    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject("test-account-id")
                        .claim("tenant_id", "scm")
                        .claim("roles", java.util.List.of("OPERATOR")));
    }
}
