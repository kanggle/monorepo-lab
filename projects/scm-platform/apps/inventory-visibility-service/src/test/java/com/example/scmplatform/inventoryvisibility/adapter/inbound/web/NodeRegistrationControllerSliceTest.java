package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService.RegisterThirdPartyLogisticsNodeResult;
import com.example.scmplatform.inventoryvisibility.config.SecurityConfig;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-SCM-BE-046 — {@code @WebMvcTest} slice for {@link NodeRegistrationController}:
 * 201 on new registration, 200 on idempotent repeat, 409 on type conflict, 422 on blank
 * input, and fail-closed (401) with no bearer token — same auth posture as the read-only
 * {@link InventoryVisibilityController} (reused {@link SecurityConfig}, no new auth surface).
 */
@WebMvcTest(controllers = NodeRegistrationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NodeRegistrationControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegisterThirdPartyLogisticsNodeService registrationService;

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
