package com.example.admin.integration;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-631 (ADR-MONO-083 P2) — the domain permission set surface against real MySQL (V0052) and a WireMock
 * account-service (effective entitled domains), with the internal assume check reading what the API wrote:
 * <ul>
 *   <li><b>AC-2</b> A/B separation: one person, an inventory set in company A and an outbound set in company B; an
 *       edit and a revoke in A leave B's effective roles and B's {@code domainRoles} byte-unchanged;</li>
 *   <li><b>AC-3</b> no {@code domain_access.manage} → 403 + DENIED row · another company → 403
 *       TENANT_SCOPE_DENIED · an unsubscribed domain → 422 · an administrator with no set → {@code domainRoles []};</li>
 *   <li><b>AC-1 (slice A half)</b> the personal revoke deletes the DIRECT row only — a {@code GROUP:<id>} row of the
 *       same set (inserted directly; the group endpoints are slice B) keeps the roles;</li>
 *   <li><b>AC-6</b> {@code domainRoles} = the union of the (operator, T) rows; {@code []} when none.</li>
 * </ul>
 * Each test seeds its own companies and operators (a fresh suffix), so nothing leaks between methods or classes.
 * Skipped without Docker (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DomainPermissionSetIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

    @BeforeAll
    static void setupShared() {
        jwt = new OperatorJwtTestFixture();
        try {
            var f = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            f.setAccessible(true);
            java.security.PrivateKey pk = ((KeyPair) f.get(jwt)).getPrivate();
            adminSigningKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> adminSigningKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    /** One test's companies and people. */
    record World(String tenantA, String tenantB, String adminA, String adminB, String supportA,
                 String worker, String workerOidc, String adminAOidc) {}

    @BeforeEach
    void stubs() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        // Every s631 company subscribes to wms only.
        wireMock.stubFor(WireMock.get(urlPathMatching("/internal/tenants/s631[a-z0-9-]*/entitled-domains"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"x\",\"domainKeys\":[\"wms\"]}")));
    }

    private World world() {
        String s = Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        World w = new World("s631a-" + s, "s631b-" + s, uuid(), uuid(), uuid(), uuid(), "oidc-631w-" + s,
                "oidc-631a-" + s);
        operator(w.adminA(), w.tenantA(), w.adminAOidc());
        operator(w.adminB(), w.tenantB(), null);
        operator(w.supportA(), w.tenantA(), null);
        operator(w.worker(), w.tenantA(), w.workerOidc());
        membership(w.adminA(), w.tenantA());
        membership(w.adminB(), w.tenantB());
        membership(w.supportA(), w.tenantA());
        membership(w.worker(), w.tenantA());
        membership(w.worker(), w.tenantB());
        role(w.adminA(), "TENANT_ADMIN", w.tenantA());
        role(w.adminB(), "TENANT_ADMIN", w.tenantB());
        role(w.supportA(), "SUPPORT_READONLY", w.tenantA());
        return w;
    }

    // ── AC-2 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-2: an edit and a revoke of the A set change A's roles only — B's domain-access and domainRoles are byte-unchanged")
    void ac2_companiesAreSeparate() throws Exception {
        World w = world();
        String setA = createSet(w.adminA(), w.tenantA(), "재고", "INVENTORY_READ", "INVENTORY_WRITE");
        String setB = createSet(w.adminB(), w.tenantB(), "출고", "OUTBOUND_READ", "OUTBOUND_WRITE");
        grant(w.adminA(), w.tenantA(), w.worker(), setA).andExpect(status().isCreated());
        grant(w.adminB(), w.tenantB(), w.worker(), setB).andExpect(status().isCreated());

        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ", "INVENTORY_WRITE");
        assertThat(domainRoles(w.workerOidc(), w.tenantB())).containsExactly("OUTBOUND_READ", "OUTBOUND_WRITE");
        String bAccessBefore = domainAccess(w.adminB(), w.tenantB(), w.worker());

        // change in A: narrow the set …
        mockMvc.perform(patch("/api/admin/domain-permission-sets/" + setA)
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantA())
                        .header("X-Operator-Reason", "ac2 narrow").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[{\"domainKey\":\"wms\",\"role\":\"INVENTORY_READ\"}]}"))
                .andExpect(status().isOk());
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ");
        assertThat(domainRoles(w.workerOidc(), w.tenantB())).containsExactly("OUTBOUND_READ", "OUTBOUND_WRITE");
        assertThat(domainAccess(w.adminB(), w.tenantB(), w.worker())).isEqualTo(bAccessBefore);

        // … then revoke it
        revoke(w.adminA(), w.tenantA(), w.worker(), setA).andExpect(status().isNoContent());
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).isEmpty();
        assertThat(domainRoles(w.workerOidc(), w.tenantB())).containsExactly("OUTBOUND_READ", "OUTBOUND_WRITE");
        assertThat(domainAccess(w.adminB(), w.tenantB(), w.worker())).isEqualTo(bAccessBefore);

        // and A's administrator cannot see B's set at all (404, not 403 — existence not disclosed)
        mockMvc.perform(get("/api/admin/domain-permission-sets/" + setB)
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantA()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_NOT_FOUND"));
    }

    // ── AC-3 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-3: no domain_access.manage → 403 PERMISSION_DENIED + DENIED row, no grant row")
    void ac3_withoutKey_403_deniedRow() throws Exception {
        World w = world();
        String setA = createSet(w.adminA(), w.tenantA(), "재고", "INVENTORY_READ");
        grant(w.supportA(), w.tenantA(), w.worker(), setA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'OPERATOR_DOMAIN_SET_GRANT' AND outcome = 'DENIED'
                """, Integer.class, w.supportA())).isEqualTo(1);
        assertThat(grantRows(w.worker())).isZero();
    }

    @Test
    @DisplayName("AC-3: A's administrator granting in B → 403 TENANT_SCOPE_DENIED; reading B's sets → 403; no row")
    void ac3_otherCompany_403() throws Exception {
        World w = world();
        String setB = createSet(w.adminB(), w.tenantB(), "출고", "OUTBOUND_READ");
        grant(w.adminA(), w.tenantB(), w.worker(), setB)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        mockMvc.perform(get("/api/admin/domain-permission-sets")
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantB()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        assertThat(grantRows(w.worker())).isZero();
    }

    @Test
    @DisplayName("AC-3: a set or a preset covering an unsubscribed domain → 422 DOMAIN_NOT_SUBSCRIBED, nothing written")
    void ac3_unsubscribed_422() throws Exception {
        World w = world();
        mockMvc.perform(post("/api/admin/domain-permission-sets")
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantA())
                        .header("X-Operator-Reason", "ac3").header("Idempotency-Key", "ac3-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        // TASK-MONO-792: SCM_OPERATOR left the catalog — a real scm catalog row keeps this about subscription
                        .content("{\"name\":\"scm\",\"roles\":[{\"domainKey\":\"scm\",\"role\":\"SCM_PROCUREMENT_READ\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_NOT_SUBSCRIBED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM domain_permission_set WHERE tenant_id = ?",
                Integer.class, w.tenantA())).isZero();

        String scmFull = jdbc.queryForObject(
                "SELECT set_id FROM domain_permission_set WHERE system_key = 'scm-full'", String.class);
        grant(w.adminA(), w.tenantA(), w.worker(), scmFull)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_NOT_SUBSCRIBED"));
        assertThat(grantRows(w.worker())).isZero();
    }

    @Test
    @DisplayName("AC-3 / AC-6: an administrator with an admin role and no set → assume check domainRoles = []")
    void ac3_adminRoleOnly_emptyDomainRoles() throws Exception {
        World w = world();
        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", w.adminAOidc()).param("tenantId", w.tenantA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.domainRoles").isArray())
                .andExpect(jsonPath("$.domainRoles").isEmpty());
    }

    // ── AC-1 (slice A half) · AC-6 ──────────────────────────────────────────

    @Test
    @DisplayName("AC-1 (A): DIRECT + a GROUP row of the same set → personal revoke removes DIRECT only; roles stay; second revoke → 404")
    void ac1_personalRevokeKeepsGroupRow() throws Exception {
        World w = world();
        String setA = createSet(w.adminA(), w.tenantA(), "재고", "INVENTORY_READ");
        grant(w.adminA(), w.tenantA(), w.worker(), setA).andExpect(status().isCreated());
        // the GROUP:<id> row slice B's fan-out would write, inserted directly
        jdbc.update("""
                INSERT INTO operator_group (group_id, tenant_id, name, description, created_by, created_at, updated_at, version)
                VALUES (?, ?, ?, NULL, NULL, NOW(6), NOW(6), 0)
                """, uuid(), w.tenantA(), "g-" + w.tenantA());
        Long groupId = jdbc.queryForObject("SELECT id FROM operator_group WHERE tenant_id = ?", Long.class,
                w.tenantA());
        jdbc.update("""
                INSERT INTO operator_domain_set_grant
                  (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
                SELECT o.id, ?, s.id, ?, ?, NULL, NOW(6)
                  FROM admin_operators o, domain_permission_set s
                 WHERE o.operator_id = ? AND s.set_id = ?
                """, w.tenantA(), "GROUP:" + groupId, groupId, w.worker(), setA);
        assertThat(sources(w.worker(), setA)).containsExactly("DIRECT", "GROUP:" + groupId);

        revoke(w.adminA(), w.tenantA(), w.worker(), setA).andExpect(status().isNoContent());

        assertThat(sources(w.worker(), setA)).containsExactly("GROUP:" + groupId);
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ");
        mockMvc.perform(get("/api/admin/operators/" + w.worker() + "/domain-access")
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets[0].sources.length()").value(1))
                .andExpect(jsonPath("$.sets[0].sources[0].source").value("GROUP"))
                .andExpect(jsonPath("$.sets[0].sources[0].groupName").value("g-" + w.tenantA()));
        assertThat(jdbc.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'OPERATOR_DOMAIN_SET_REVOKE' AND outcome = 'SUCCESS'
                """, String.class, w.adminA()))
                .isEqualTo("setId=" + setA + " removedSources=DIRECT keptGroupSources=1");

        revoke(w.adminA(), w.tenantA(), w.worker(), setA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-6: domainRoles = union of two sets in T (catalog order); DIRECT re-grant → 409; set in use → delete 409")
    void ac6_unionAndConflicts() throws Exception {
        World w = world();
        String outbound = createSet(w.adminA(), w.tenantA(), "출고", "OUTBOUND_WRITE", "OUTBOUND_READ");
        String preset = jdbc.queryForObject(
                "SELECT set_id FROM domain_permission_set WHERE system_key = 'wms-inventory-read'", String.class);
        grant(w.adminA(), w.tenantA(), w.worker(), outbound).andExpect(status().isCreated());
        grant(w.adminA(), w.tenantA(), w.worker(), preset)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selfGrant").value(false));

        assertThat(domainRoles(w.workerOidc(), w.tenantA()))
                .containsExactly("INVENTORY_READ", "OUTBOUND_READ", "OUTBOUND_WRITE");

        grant(w.adminA(), w.tenantA(), w.worker(), outbound)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_ALREADY_GRANTED"));
        mockMvc.perform(delete("/api/admin/domain-permission-sets/" + outbound)
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantA())
                        .header("X-Operator-Reason", "ac6"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_IN_USE"));
        mockMvc.perform(patch("/api/admin/domain-permission-sets/" + preset)
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantA())
                        .header("X-Operator-Reason", "ac6").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mine\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_READ_ONLY"));
        // the grant is audited with domain_access.manage and the target tenant
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'OPERATOR_DOMAIN_SET_GRANT' AND outcome = 'SUCCESS'
                   AND target_type = 'OPERATOR' AND target_id = ? AND target_tenant_id = ?
                   AND permission_used = 'domain_access.manage'
                """, Integer.class, w.adminA(), w.worker(), w.tenantA())).isEqualTo(2);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String createSet(String actor, String tenant, String name, String... roles) throws Exception {
        StringBuilder json = new StringBuilder();
        for (String r : roles) {
            if (json.length() > 0) json.append(',');
            json.append("{\"domainKey\":\"wms\",\"role\":\"").append(r).append("\"}");
        }
        String body = mockMvc.perform(post("/api/admin/domain-permission-sets")
                        .header("Authorization", bearer(actor)).header("X-Tenant-Id", tenant)
                        .header("X-Operator-Reason", "it").header("Idempotency-Key", "it-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"roles\":[" + json + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.readOnly").value(false))
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"setId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private ResultActions grant(String actor, String tenant, String target, String setId) throws Exception {
        return mockMvc.perform(post("/api/admin/operators/" + target + "/domain-permission-sets")
                .header("Authorization", bearer(actor)).header("X-Tenant-Id", tenant)
                .header("X-Operator-Reason", "it grant").contentType(MediaType.APPLICATION_JSON)
                .content("{\"setId\":\"" + setId + "\"}"));
    }

    private ResultActions revoke(String actor, String tenant, String target, String setId) throws Exception {
        return mockMvc.perform(delete("/api/admin/operators/" + target + "/domain-permission-sets/" + setId)
                .header("Authorization", bearer(actor)).header("X-Tenant-Id", tenant)
                .header("X-Operator-Reason", "it revoke"));
    }

    private String domainAccess(String actor, String tenant, String target) throws Exception {
        return mockMvc.perform(get("/api/admin/operators/" + target + "/domain-access")
                        .header("Authorization", bearer(actor)).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> domainRoles(String oidcSubject, String tenant) throws Exception {
        String body = mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", oidcSubject).param("tenantId", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.domainRoles").isArray())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode roles =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("domainRoles");
        List<String> out = new java.util.ArrayList<>();
        roles.forEach(n -> out.add(n.asText()));
        return out;
    }

    private int grantRows(String operatorUuid) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM operator_domain_set_grant g JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = ?
                """, Integer.class, operatorUuid);
        return n == null ? -1 : n;
    }

    private List<String> sources(String operatorUuid, String setId) {
        return jdbc.queryForList("""
                SELECT g.source_key FROM operator_domain_set_grant g
                  JOIN admin_operators o ON o.id = g.operator_id
                  JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE o.operator_id = ? AND s.set_id = ?
                 ORDER BY g.source_key
                """, String.class, operatorUuid, setId);
    }

    private void operator(String uuid, String home, String oidcSubject) {
        jdbc.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, oidc_subject,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, NULL, 'BE-631 op', 'ACTIVE', ?, NOW(6), NOW(6), 0)
                """, uuid, home, uuid + "@example.com", oidcSubject);
    }

    private void membership(String uuid, String tenant) {
        jdbc.update("""
                INSERT INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
                SELECT o.id, ?, NOW(6), NULL, NULL FROM admin_operators o WHERE o.operator_id = ?
                """, tenant, uuid);
    }

    private void role(String uuid, String roleName, String tenant) {
        jdbc.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, ?, NOW(6), NULL FROM admin_operators o JOIN admin_roles r ON r.name = ?
                 WHERE o.operator_id = ?
                """, tenant, roleName, uuid);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }
}
