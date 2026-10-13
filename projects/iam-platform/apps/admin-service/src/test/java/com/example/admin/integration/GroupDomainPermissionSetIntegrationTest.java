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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-631 slice B (ADR-MONO-083 D4 · D5) — group domain permission sets against real MySQL (V0052) and a WireMock
 * account-service (entitled domains = wms), with the internal assume check reading what the API wrote:
 * <ul>
 *   <li><b>AC-1</b> (full control, failure side first): DIRECT + G1 + G2 give the same set → leave G1 → still
 *       effective ({DIRECT, GROUP:G2}) → personal revoke → still effective ({GROUP:G2}) → G2's set revoke → gone. The
 *       source list and {@code domainRoles} are asserted at every step;</li>
 *   <li><b>member-add gate</b>: {@code group.manage} without {@code domain_access.manage} adding a member to a group
 *       that carries a set → 403 + DENIED row, no row of any kind; the same actor on a set-less group → 201;</li>
 *   <li><b>AC-3</b> group set rows land in the group's tenant only (the member also belongs to B);</li>
 *   <li><b>AC-4</b> «이 회사에서 내보내기» from A → A's set rows of every source 0, B's rows byte-unchanged, audit
 *       {@code domainSetGrants};</li>
 *   <li>group delete removes only that group's rows (and its templates).</li>
 * </ul>
 * Each test seeds its own companies and people. Skipped without Docker (AbstractIntegrationTest) — CI Linux is
 * authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class GroupDomainPermissionSetIntegrationTest extends AbstractIntegrationTest {

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

    /** One test's companies and people. {@code groupMgr} holds group.manage in A but NOT domain_access.manage. */
    record World(String tenantA, String tenantB, String adminA, String adminB, String worker, String workerOidc,
                 String groupMgr) {}

    @BeforeEach
    void stubs() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wireMock.stubFor(WireMock.get(urlPathMatching("/internal/tenants/s631[a-z0-9-]*/entitled-domains"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"x\",\"domainKeys\":[\"wms\"]}")));
    }

    private World world() {
        String s = Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        World w = new World("s631a-" + s, "s631b-" + s, uuid(), uuid(), uuid(), "oidc-631gw-" + s, uuid());
        operator(w.adminA(), w.tenantA(), null);
        operator(w.adminB(), w.tenantB(), null);
        operator(w.worker(), w.tenantA(), w.workerOidc());
        operator(w.groupMgr(), w.tenantA(), null);
        membership(w.adminA(), w.tenantA());
        membership(w.adminB(), w.tenantB());
        membership(w.worker(), w.tenantA());
        membership(w.worker(), w.tenantB());
        membership(w.groupMgr(), w.tenantA());
        role(w.adminA(), "TENANT_ADMIN", w.tenantA());
        role(w.adminB(), "TENANT_ADMIN", w.tenantB());
        // a custom role: group.manage only (the ORG_ADMIN-like shape the member-add gate is about)
        String roleName = "S631_GRPMGR_" + s;
        jdbc.update("INSERT INTO admin_roles (name, description, require_2fa, created_at) VALUES (?, ?, false, NOW(6))",
                roleName, "BE-631 group.manage only");
        jdbc.update("INSERT INTO admin_role_permissions (role_id, permission_key) "
                + "SELECT id, 'group.manage' FROM admin_roles WHERE name = ?", roleName);
        role(w.groupMgr(), roleName, w.tenantA());
        return w;
    }

    // ── AC-1 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-1: DIRECT + G1 + G2 → leave G1 (kept: DIRECT, G2) → personal revoke (kept: G2) → G2 set revoke → gone")
    void ac1_fullControl_eachSourceRemovesItsOwnRow() throws Exception {
        World w = world();
        String set = createSet(w.adminA(), w.tenantA(), "재고", "INVENTORY_READ", "INVENTORY_WRITE");
        String g1 = createGroup(w.adminA(), w.tenantA(), "g1");
        String g2 = createGroup(w.adminA(), w.tenantA(), "g2");
        long g1pk = groupPk(g1);
        long g2pk = groupPk(g2);

        grantDirect(w.adminA(), w.tenantA(), w.worker(), set).andExpect(status().isCreated());
        // G1: the set first, then the member (member-add fan-out path)
        grantToGroup(w.adminA(), g1, set).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fannedOutRows").value(0))
                .andExpect(jsonPath("$.item.setId").value(set));
        addMember(w.adminA(), g1, w.worker()).andExpect(status().isCreated());
        // G2: the member first, then the set (set-grant fan-out path) — not skipped although DIRECT + G1 already give it
        addMember(w.adminA(), g2, w.worker()).andExpect(status().isCreated());
        grantToGroup(w.adminA(), g2, set).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fannedOutRows").value(1));

        // (source keys compare as strings — "GROUP:10" < "GROUP:9" — so the order is not asserted, the set is)
        assertThat(sources(w.worker(), set)).containsExactlyInAnyOrder("DIRECT", "GROUP:" + g1pk, "GROUP:" + g2pk);
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ", "INVENTORY_WRITE");

        // 1. leave G1 → only GROUP:G1 goes
        mockMvc.perform(delete("/api/admin/groups/" + g1 + "/members/" + w.worker())
                        .header("Authorization", bearer(w.adminA())).header("X-Operator-Reason", "ac1 leave"))
                .andExpect(status().isNoContent());
        assertThat(sources(w.worker(), set)).containsExactly("DIRECT", "GROUP:" + g2pk);
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ", "INVENTORY_WRITE");

        // 2. personal revoke → only DIRECT goes
        mockMvc.perform(delete("/api/admin/operators/" + w.worker() + "/domain-permission-sets/" + set)
                        .header("Authorization", bearer(w.adminA())).header("X-Tenant-Id", w.tenantA())
                        .header("X-Operator-Reason", "ac1 personal"))
                .andExpect(status().isNoContent());
        assertThat(sources(w.worker(), set)).containsExactly("GROUP:" + g2pk);
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ", "INVENTORY_WRITE");

        // 3. G2's set revoke → the last source goes, and with it the roles
        mockMvc.perform(delete("/api/admin/groups/" + g2 + "/domain-permission-sets/" + set)
                        .header("Authorization", bearer(w.adminA())).header("X-Operator-Reason", "ac1 g2 revoke"))
                .andExpect(status().isNoContent());
        assertThat(sources(w.worker(), set)).isEmpty();
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).isEmpty();
        assertThat(templates(g2pk)).isZero();
        assertThat(templates(g1pk)).as("G1 still carries the set (only its member left)").isEqualTo(1);

        assertThat(jdbc.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'GROUP_DOMAIN_SET_REVOKE' AND outcome = 'SUCCESS'
                   AND target_type = 'GROUP' AND target_id = ? AND target_tenant_id = ?
                   AND permission_used = 'domain_access.manage'
                """, String.class, w.adminA(), g2, w.tenantA())).isEqualTo("setId=" + set + " removedRows=1");
        assertThat(jdbc.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'GROUP_DOMAIN_SET_GRANT' AND outcome = 'SUCCESS' AND target_id = ?
                """, String.class, w.adminA(), g2)).isEqualTo("setId=" + set + " fannedOutRows=1 selfGrant=false");

        // the same set again on G1 → 409
        grantToGroup(w.adminA(), g1, set).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_ALREADY_GRANTED"));
        mockMvc.perform(get("/api/admin/groups/" + g1 + "/domain-permission-sets")
                        .header("Authorization", bearer(w.adminA())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].setId").value(set))
                .andExpect(jsonPath("$.items[0].grantedBy").value(w.adminA()));
    }

    // ── member-add gate ─────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 member add: group.manage without domain_access.manage on a group carrying a set → 403 + DENIED row, no row of any kind; set-less group → 201 (control)")
    void memberAdd_groupWithSet_needsDomainAccessManage() throws Exception {
        World w = world();
        String set = createSet(w.adminA(), w.tenantA(), "재고", "INVENTORY_READ");
        String withSet = createGroup(w.adminA(), w.tenantA(), "with-set");
        String withoutSet = createGroup(w.adminA(), w.tenantA(), "without-set");
        grantToGroup(w.adminA(), withSet, set).andExpect(status().isCreated());

        // failure side first
        addMember(w.groupMgr(), withSet, w.worker())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'GROUP_MEMBER_ADD' AND outcome = 'DENIED'
                   AND permission_used = 'domain_access.manage'
                """, Integer.class, w.groupMgr())).isEqualTo(1);
        assertThat(members(groupPk(withSet))).isZero();
        assertThat(grantRows(w.worker())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles r JOIN admin_operators o ON o.id = r.operator_id
                 WHERE o.operator_id = ?
                """, Integer.class, w.worker())).isZero();

        // control: the same actor, a group with no set → the old rule (group.manage is enough)
        addMember(w.groupMgr(), withoutSet, w.worker()).andExpect(status().isCreated());
        assertThat(members(groupPk(withoutSet))).isEqualTo(1);
        assertThat(grantRows(w.worker())).isZero();
        // and with domain_access.manage the set-carrying group accepts the member, fanning the set out
        addMember(w.adminA(), withSet, w.worker()).andExpect(status().isCreated());
        assertThat(sources(w.worker(), set)).containsExactly("GROUP:" + groupPk(withSet));
    }

    // ── AC-3: group tenant only ─────────────────────────────────────────────

    @Test
    @DisplayName("AC-3: group set rows land in the group's tenant only (the member also belongs to B); B's domainRoles stay []")
    void groupSetRows_onlyInGroupTenant() throws Exception {
        World w = world();
        String preset = jdbc.queryForObject(
                "SELECT set_id FROM domain_permission_set WHERE system_key = 'wms-inventory-read'", String.class);
        String g = createGroup(w.adminA(), w.tenantA(), "g");
        addMember(w.adminA(), g, w.worker()).andExpect(status().isCreated());
        grantToGroup(w.adminA(), g, preset).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fannedOutRows").value(1));

        assertThat(jdbc.queryForList("""
                SELECT DISTINCT g.tenant_id FROM operator_domain_set_grant g JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = ?
                """, String.class, w.worker())).containsExactly(w.tenantA());
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ");
        assertThat(domainRoles(w.workerOidc(), w.tenantB())).isEmpty();

        // B's administrator cannot touch A's group (403 on writes, 404 on the read)
        grantToGroup(w.adminB(), g, preset).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        mockMvc.perform(get("/api/admin/groups/" + g + "/domain-permission-sets")
                        .header("Authorization", bearer(w.adminB())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        // a set of B cannot be put on A's group
        String setB = createSet(w.adminB(), w.tenantB(), "출고", "OUTBOUND_READ");
        grantToGroup(w.adminA(), g, setB).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_NOT_FOUND"));
    }

    // ── AC-4: export ────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-4: export from A → A's set rows of every source (DIRECT · MIGRATION · GROUP) 0; B's rows byte-unchanged; audit domainSetGrants=3")
    void ac4_exportRemovesEverySourceInA_leavesB() throws Exception {
        World w = world();
        String setA = createSet(w.adminA(), w.tenantA(), "재고", "INVENTORY_READ");
        String setB = createSet(w.adminB(), w.tenantB(), "출고", "OUTBOUND_READ");
        grantDirect(w.adminA(), w.tenantA(), w.worker(), setA).andExpect(status().isCreated());
        String g = createGroup(w.adminA(), w.tenantA(), "g");
        grantToGroup(w.adminA(), g, setA).andExpect(status().isCreated());
        addMember(w.adminA(), g, w.worker()).andExpect(status().isCreated());
        // the MIGRATION row the V0052 backfill would have written (the membership was seeded after it ran)
        jdbc.update("""
                INSERT INTO operator_domain_set_grant
                  (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
                SELECT o.id, ?, s.id, 'MIGRATION', NULL, NULL, NOW(6)
                  FROM admin_operators o, domain_permission_set s
                 WHERE o.operator_id = ? AND s.system_key = 'legacy-compat'
                """, w.tenantA(), w.worker());
        grantDirect(w.adminB(), w.tenantB(), w.worker(), setB).andExpect(status().isCreated());
        assertThat(rowsIn(w.worker(), w.tenantA())).hasSize(3);
        List<String> bBefore = rowsIn(w.worker(), w.tenantB());
        assertThat(bBefore).hasSize(1);

        mockMvc.perform(delete("/api/admin/operators/" + w.worker() + "/assignments/" + w.tenantA())
                        .header("Authorization", bearer(w.adminA())).header("X-Operator-Reason", "ac4 exit"))
                // A fail-closed audit wraps the real cause (AUDIT_FAILURE / 500) and the app logs are off in this
                // profile — print the whole cause chain so a red run names its own cause in the report.
                .andDo(r -> {
                    for (Throwable t = r.getResolvedException(); t != null; t = t.getCause()) {
                        System.out.println("CAUSE> " + t);
                    }
                })
                .andExpect(status().isNoContent());

        assertThat(rowsIn(w.worker(), w.tenantA())).as("A rows of every source").isEmpty();
        assertThat(rowsIn(w.worker(), w.tenantB())).as("B rows byte-unchanged").isEqualTo(bBefore);
        assertThat(domainRoles(w.workerOidc(), w.tenantB())).containsExactly("OUTBOUND_READ");
        assertThat(jdbc.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'OPERATOR_ASSIGNMENT_DELETE' AND outcome = 'SUCCESS'
                   AND target_id = ?
                """, String.class, w.adminA(), w.worker())).endsWith(",domainSetGrants=3");
        assertThat(templates(groupPk(g))).as("the group keeps its set; only the person left").isEqualTo(1);
    }

    // ── group delete ────────────────────────────────────────────────────────

    @Test
    @DisplayName("group delete removes only that group's rows and templates — DIRECT and the other group's rows stay")
    void groupDelete_removesOnlyItsOwnRows() throws Exception {
        World w = world();
        String set = createSet(w.adminA(), w.tenantA(), "재고", "INVENTORY_READ");
        grantDirect(w.adminA(), w.tenantA(), w.worker(), set).andExpect(status().isCreated());
        String g1 = createGroup(w.adminA(), w.tenantA(), "g1");
        String g2 = createGroup(w.adminA(), w.tenantA(), "g2");
        long g1pk = groupPk(g1);
        long g2pk = groupPk(g2);
        for (String g : List.of(g1, g2)) {
            addMember(w.adminA(), g, w.worker()).andExpect(status().isCreated());
            grantToGroup(w.adminA(), g, set).andExpect(status().isCreated());
        }
        assertThat(sources(w.worker(), set)).containsExactlyInAnyOrder("DIRECT", "GROUP:" + g1pk, "GROUP:" + g2pk);

        mockMvc.perform(delete("/api/admin/groups/" + g1)
                        .header("Authorization", bearer(w.adminA())).header("X-Operator-Reason", "delete g1"))
                .andExpect(status().isNoContent());

        assertThat(sources(w.worker(), set)).containsExactly("DIRECT", "GROUP:" + g2pk);
        assertThat(templates(g1pk)).isZero();
        assertThat(templates(g2pk)).isEqualTo(1);
        assertThat(domainRoles(w.workerOidc(), w.tenantA())).containsExactly("INVENTORY_READ");
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
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"setId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String createGroup(String actor, String tenant, String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(actor)).header("X-Operator-Reason", "it group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenant + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"groupId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private ResultActions grantToGroup(String actor, String groupId, String setId) throws Exception {
        return mockMvc.perform(post("/api/admin/groups/" + groupId + "/domain-permission-sets")
                .header("Authorization", bearer(actor)).header("X-Operator-Reason", "it group set")
                .header("Idempotency-Key", "it-g-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"setId\":\"" + setId + "\"}"));
    }

    private ResultActions addMember(String actor, String groupId, String operator) throws Exception {
        return mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                .header("Authorization", bearer(actor)).header("X-Operator-Reason", "it member")
                .header("Idempotency-Key", "it-m-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"operatorId\":\"" + operator + "\"}"));
    }

    private ResultActions grantDirect(String actor, String tenant, String target, String setId) throws Exception {
        return mockMvc.perform(post("/api/admin/operators/" + target + "/domain-permission-sets")
                .header("Authorization", bearer(actor)).header("X-Tenant-Id", tenant)
                .header("X-Operator-Reason", "it grant").contentType(MediaType.APPLICATION_JSON)
                .content("{\"setId\":\"" + setId + "\"}"));
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
        List<String> out = new ArrayList<>();
        roles.forEach(n -> out.add(n.asText()));
        return out;
    }

    private long groupPk(String groupId) {
        Long id = jdbc.queryForObject("SELECT id FROM operator_group WHERE group_id = ?", Long.class, groupId);
        return id == null ? -1 : id;
    }

    private int templates(long groupPk) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM operator_group_domain_set_grant WHERE group_id = ?",
                Integer.class, groupPk);
        return n == null ? -1 : n;
    }

    private int members(long groupPk) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM operator_group_member WHERE group_id = ?",
                Integer.class, groupPk);
        return n == null ? -1 : n;
    }

    private int grantRows(String operatorUuid) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM operator_domain_set_grant g JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = ?
                """, Integer.class, operatorUuid);
        return n == null ? -1 : n;
    }

    /** The source keys of (operator, set), sorted — the AC-1 «출처 목록». */
    private List<String> sources(String operatorUuid, String setId) {
        return jdbc.queryForList("""
                SELECT g.source_key FROM operator_domain_set_grant g
                  JOIN admin_operators o ON o.id = g.operator_id
                  JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE o.operator_id = ? AND s.set_id = ?
                 ORDER BY g.source_key
                """, String.class, operatorUuid, setId);
    }

    /** Every column of every set row of (operator, tenant), one string per row — «byte-unchanged» is string equality. */
    private List<String> rowsIn(String operatorUuid, String tenant) {
        return jdbc.queryForList("""
                SELECT CONCAT_WS('|', g.operator_id, g.tenant_id, g.set_id, g.source_key,
                                 COALESCE(g.group_origin, '-'), COALESCE(g.granted_by, '-'),
                                 DATE_FORMAT(g.granted_at, '%Y-%m-%d %H:%i:%s.%f'))
                  FROM operator_domain_set_grant g JOIN admin_operators o ON o.id = g.operator_id
                 WHERE o.operator_id = ? AND g.tenant_id = ?
                 ORDER BY g.set_id, g.source_key
                """, String.class, operatorUuid, tenant);
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
