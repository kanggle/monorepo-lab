package com.example.admin.integration;

import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TenantOrgNodeConflictException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.application.orgnode.TenantPlacementView;
import com.example.admin.application.port.OrgNodePort;
import com.example.admin.application.port.TenantPlacementPort;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07, «양쪽을 다 관리하는 사람만») — the placement write
 * end-to-end through the real RBAC aspect, the real {@code AdminGrantScopeEvaluator} (grant rows in
 * a real MySQL), the real guard and the real audit writer.
 *
 * <p>The account-service authority is the seam, as in {@code OrgNodeAdminIntegrationTest}:
 * {@link OrgNodePort} and {@link TenantPlacementPort} are replaced by a small in-memory model of
 * the tree and of each tenant's placement, so "소속 불변" is asserted on state, not on a mock call.
 * Tenant creation ({@code POST /internal/tenants}) goes to WireMock.
 *
 * <p>Tree: {@code holding ── wms-div ── (acme-wms)}, {@code holding ── erp-div},
 * {@code holding ── other-div ── (other-svc)}; {@code loose-svc} and {@code new-svc} are ungrouped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class TenantOrgNodePlacementIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    // Task-scoped operator UUIDs (house convention ...-00000be0<task><nn>; case-insensitive unique).
    static final String SUPER_UUID = "00000000-0000-7000-8000-00000be62501";
    static final String DEST_ADMIN_UUID = "00000000-0000-7000-8000-00000be62502";     // ORG_ADMIN @ erp-div
    static final String HOLDING_ADMIN_UUID = "00000000-0000-7000-8000-00000be62503";  // ORG_ADMIN @ holding
    static final String TENANT_ADMIN_UUID = "00000000-0000-7000-8000-00000be62504";   // TENANT_ADMIN @ acme-wms only
    static final String OWNER_DEST_UUID = "00000000-0000-7000-8000-00000be62505";     // TENANT_ADMIN @ new-svc + ORG_ADMIN @ erp-div

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var field = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            field.setAccessible(true);
            return ((java.security.KeyPair) field.get(fixture)).getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> signingKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrgNodeSubtreeResolver subtreeResolver;

    @MockitoBean OrgNodePort orgNodePort;
    @MockitoBean TenantPlacementPort placementPort;

    /** The authority's state: tenant → its node (Optional.empty() = ungrouped). */
    private final Map<String, Optional<String>> placement = new HashMap<>();
    private static final List<String> NODES = List.of("holding", "wms-div", "erp-div", "other-div");

    private static OrgNodeView node(String id, String parentId) {
        return new OrgNodeView(id, parentId, "n-" + id, parentId == null ? 1 : 2,
                CeilingView.unbounded(), Instant.EPOCH, Instant.EPOCH);
    }

    @BeforeEach
    void seed() {
        subtreeResolver.invalidateAll();
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));

        placement.clear();
        placement.put("acme-wms", Optional.of("wms-div"));
        placement.put("other-svc", Optional.of("other-div"));
        placement.put("loose-svc", Optional.empty());
        placement.put("new-svc", Optional.empty());

        when(orgNodePort.list()).thenReturn(List.of(
                node("holding", null), node("wms-div", "holding"), node("erp-div", "holding"),
                node("other-div", "holding")));
        when(orgNodePort.subtreeTenantIds(anyString())).thenAnswer(inv -> subtree(inv.getArgument(0)));
        when(placementPort.currentOrgNodeId(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            if (!placement.containsKey(t)) throw new TenantNotFoundException(t);
            return placement.get(t).orElse(null);
        });
        when(placementPort.place(anyString(), any(), any())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            String target = inv.getArgument(1);
            String expected = inv.getArgument(2);
            if (!placement.containsKey(t)) throw new TenantNotFoundException(t);
            String current = placement.get(t).orElse(null);
            if (!Objects.equals(current, expected)) throw new TenantOrgNodeConflictException("moved");
            if (target != null && !NODES.contains(target)) throw new OrgNodeNotFoundException("gone: " + target);
            boolean changed = !Objects.equals(current, target);
            placement.put(t, Optional.ofNullable(target));
            return new TenantPlacementView(t, current, target, List.of(), List.of(), List.of(), List.of(), changed);
        });

        seedOperator(SUPER_UUID, "*", "be625-super@example.com", "SUPER_ADMIN", null);
        seedOperator(DEST_ADMIN_UUID, "acme-hq", "be625-dest@example.com", "ORG_ADMIN", "erp-div");
        seedOperator(HOLDING_ADMIN_UUID, "acme-hq", "be625-holding@example.com", "ORG_ADMIN", "holding");
        seedOperator(TENANT_ADMIN_UUID, "acme-wms", "be625-tadmin@example.com", "TENANT_ADMIN", null);
        seedOperator(OWNER_DEST_UUID, "new-svc", "be625-owner@example.com", "TENANT_ADMIN", null);
        grantNodeRole(OWNER_DEST_UUID, "ORG_ADMIN", "erp-div");
    }

    /** holding's subtree is every node; any other node is a leaf here. */
    private List<String> subtree(String nodeId) {
        return placement.entrySet().stream()
                .filter(e -> e.getValue().isPresent()
                        && ("holding".equals(nodeId) || e.getValue().get().equals(nodeId)))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    // ── AC-1 — the isolation control group, failing side first ─────────────────────

    @Test
    @DisplayName("AC-1: ORG_ADMIN @ erp-div pulling other-svc in → 404 TENANT_NOT_FOUND, placement unchanged, DENIED row; "
            + "the holding admin (both sides) → 200 and a SUCCESS row with from/to + reason")
    void destinationOnlyAdmin_cannotPullIn_bothSidesAdminCan() throws Exception {
        mockMvc.perform(placeRequest("other-svc", DEST_ADMIN_UUID, "\"erp-div\"", "pull in"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));

        assertThat(placement.get("other-svc")).contains("other-div");
        assertThat(countRows(DEST_ADMIN_UUID, "DENIED", "other-svc")).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                WHERE actor_id = ? AND outcome = 'DENIED' AND target_id = 'other-svc'
                ORDER BY started_at DESC LIMIT 1
                """, String.class, DEST_ADMIN_UUID)).contains("side=SOURCE");

        mockMvc.perform(placeRequest("other-svc", HOLDING_ADMIN_UUID, "\"erp-div\"", "erp 로 이관"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromOrgNodeId").value("other-div"))
                .andExpect(jsonPath("$.toOrgNodeId").value("erp-div"))
                .andExpect(jsonPath("$.changed").value(true));

        assertThat(placement.get("other-svc")).contains("erp-div");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                WHERE actor_id = ? AND outcome = 'SUCCESS' AND action_code = 'TENANT_ORG_NODE_ASSIGN'
                  AND target_type = 'TENANT' AND target_id = 'other-svc'
                ORDER BY started_at DESC LIMIT 1
                """, String.class, HOLDING_ADMIN_UUID))
                .isEqualTo("from_org_node_id=other-div to_org_node_id=erp-div changed=true");
    }

    // ── AC-2 ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-2: TENANT_ADMIN @ acme-wms cannot detach it from wms-div (403 at the org.manage gate); the holding admin can")
    void tenantOwnerCannotDetach_sourceAdminCan() throws Exception {
        mockMvc.perform(placeRequest("acme-wms", TENANT_ADMIN_UUID, "null", "leave"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(placement.get("acme-wms")).contains("wms-div");
        verify(placementPort, never()).place(anyString(), any(), any());

        mockMvc.perform(placeRequest("acme-wms", HOLDING_ADMIN_UUID, "null", "spin off"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromOrgNodeId").value("wms-div"));
        assertThat(placement.get("acme-wms")).isEmpty();
    }

    // ── AC-3 — rider P1 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-3: TENANT_ADMIN @ new-svc + ORG_ADMIN @ erp-div attaches the ungrouped new-svc; "
            + "ORG_ADMIN @ erp-div alone cannot attach the ungrouped loose-svc (404)")
    void riderP1() throws Exception {
        mockMvc.perform(placeRequest("new-svc", OWNER_DEST_UUID, "\"erp-div\"", "join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toOrgNodeId").value("erp-div"));
        assertThat(placement.get("new-svc")).contains("erp-div");

        mockMvc.perform(placeRequest("loose-svc", DEST_ADMIN_UUID, "\"erp-div\"", "grab"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
        assertThat(placement.get("loose-svc")).isEmpty();
    }

    // ── AC-6 ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-6: the same PUT twice → same state, second answer changed=false, one audit row each")
    void repeatIsIdempotent() throws Exception {
        mockMvc.perform(placeRequest("acme-wms", SUPER_UUID, "\"erp-div\"", "move"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));
        mockMvc.perform(placeRequest("acme-wms", SUPER_UUID, "\"erp-div\"", "move"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        assertThat(placement.get("acme-wms")).contains("erp-div");
        assertThat(countRows(SUPER_UUID, "SUCCESS", "acme-wms")).isGreaterThanOrEqualTo(2);
    }

    // ── AC-5 ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-5: POST /api/admin/tenants with orgNodeId → created, then placed under the node; without it → no placement call")
    void createUnderNode_andRegression() throws Exception {
        stubTenantCreate("brand-new");
        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"brand-new","displayName":"Brand New","tenantType":"B2B_ENTERPRISE","orgNodeId":"erp-div"}
                                """))
                .andExpect(status().isCreated());
        verify(placementPort).place("brand-new", "erp-div", null);

        stubTenantCreate("plain-new");
        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"plain-new","displayName":"Plain New","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isCreated());
        verify(placementPort, never()).place(org.mockito.ArgumentMatchers.eq("plain-new"), any(), any());
    }

    // ── fixtures ────────────────────────────────────────────────────────────────

    /** Placement fake: a tenant created through WireMock becomes known (ungrouped) to the authority. */
    private void stubTenantCreate(String tenantId) {
        placement.put(tenantId, Optional.empty());
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/internal/tenants"))
                .withRequestBody(WireMock.containing("\"" + tenantId + "\""))
                .willReturn(WireMock.aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"tenantId":"%s","displayName":"X","tenantType":"B2B_ENTERPRISE","status":"ACTIVE",
                                 "createdAt":"2026-10-08T00:00:00Z","updatedAt":"2026-10-08T00:00:00Z"}
                                """.formatted(tenantId))));
    }

    private org.springframework.test.web.servlet.RequestBuilder placeRequest(
            String tenantId, String actorUuid, String orgNodeJson, String reason) {
        return put("/api/admin/tenants/" + tenantId + "/org-node")
                .header("Authorization", bearer(actorUuid))
                .header("X-Operator-Reason", java.net.URLEncoder.encode(reason, java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgNodeId\":" + orgNodeJson + "}");
    }

    private Integer countRows(String actorUuid, String outcome, String tenantId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                WHERE actor_id = ? AND outcome = ? AND action_code = 'TENANT_ORG_NODE_ASSIGN'
                  AND target_type = 'TENANT' AND target_id = ?
                """, Integer.class, actorUuid, outcome, tenantId);
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    private void seedOperator(String uuid, String tenantId, String email, String roleName, String orgNodeId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, uuid, tenantId, email, "BE-625 Op");
        }
        String actualTenant = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM admin_operators WHERE operator_id = ?", String.class, uuid);
        assertThat(actualTenant)
                .as("operator %s must be owned by this test class (tenant_id skew ⇒ uuid collision)", uuid)
                .isEqualTo(tenantId);
        if (orgNodeId == null) {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                    SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                      FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = ?
                    """, uuid, roleName);
        } else {
            grantNodeRole(uuid, roleName, orgNodeId);
        }
    }

    private void grantNodeRole(String uuid, String roleName, String orgNodeId) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, org_node_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, ?, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, orgNodeId, uuid, roleName);
    }
}
