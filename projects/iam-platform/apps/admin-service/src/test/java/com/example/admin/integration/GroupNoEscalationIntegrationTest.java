package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-520 / ADR-MONO-046 D4 (AC-4) — no-escalation: a granter cannot grant a group a role
 * or tenant-assignment it does not itself hold, checked at grant time AND at add-member
 * fan-out time; {@code SUPER_ADMIN} net-zero.
 *
 * <p>{@code TENANT_ADMIN} holds {@code {operator.manage, tenant.admin.delegate,
 * partnership.manage, group.manage}} scoped to acme — it does NOT hold {@code account.lock}
 * (SUPPORT_LOCK's permission) and its {@code operator.manage} scope is {@code {acme}}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class GroupNoEscalationIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;
    static final ObjectMapper MAPPER = new ObjectMapper();

    static final String SUPER_UUID = "00000000-0000-7000-8000-00000be05221";
    static final String TA_ACME_UUID = "00000000-0000-7000-8000-00000be05222";
    static final String MEMBER_UUID = "00000000-0000-7000-8000-00000be05223";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var field = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            field.setAccessible(true);
            java.security.KeyPair kp = (java.security.KeyPair) field.get(fixture);
            return kp.getPrivate();
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
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18086");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        GroupItSeeds.seedOperator(jdbcTemplate, SUPER_UUID, "*", "grpesc-super@example.com", "SUPER_ADMIN");
        GroupItSeeds.seedOperator(jdbcTemplate, TA_ACME_UUID, "acme", "grpesc-ta@example.com", "TENANT_ADMIN");
        GroupItSeeds.seedOperator(jdbcTemplate, MEMBER_UUID, "acme", "grpesc-m@example.com", null);
    }

    @Test
    @DisplayName("grant-time: TENANT_ADMIN cannot grant a group SUPPORT_LOCK (a permission it lacks) → 403 ROLE_GRANT_FORBIDDEN")
    void grantRoleExceedingOwn_forbidden() throws Exception {
        String groupId = createGroup(TA_ACME_UUID, "acme", "esc 팀 A");
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "escalate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPPORT_LOCK\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_GRANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("grant-time: TENANT_ADMIN cannot grant a group SUPER_ADMIN → 403 ROLE_GRANT_FORBIDDEN")
    void grantSuperAdmin_forbidden() throws Exception {
        String groupId = createGroup(TA_ACME_UUID, "acme", "esc 팀 B");
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "escalate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_GRANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("grant-time: TENANT_ADMIN cannot grant a group an assignment to a tenant outside its scope → 422")
    void grantTenantOutsideScope_unprocessable() throws Exception {
        String groupId = createGroup(TA_ACME_UUID, "acme", "esc 팀 C");
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "escalate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantAssignments\":[{\"tenantId\":\"globex\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GROUP_GRANT_NO_ESCALATION"));
    }

    @Test
    @DisplayName("grant-time: TENANT_ADMIN CAN grant an assignment to its own tenant (within scope) → 201")
    void grantTenantWithinScope_ok() throws Exception {
        String groupId = createGroup(TA_ACME_UUID, "acme", "esc 팀 D");
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantAssignments\":[{\"tenantId\":\"acme\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].type").value("TENANT_ASSIGNMENT"));
    }

    @Test
    @DisplayName("SUPER_ADMIN net-zero: granting SUPPORT_LOCK to a group is unconstrained → 201")
    void superAdmin_netZero() throws Exception {
        String groupId = createGroup(SUPER_UUID, "acme", "esc 팀 E");
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPPORT_LOCK\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("add-member time: the fan-out re-check catches an escalation the adder no longer covers → 403")
    void addMemberReCheck_forbidden() throws Exception {
        // SUPER seeds a group in acme WITH a SUPPORT_LOCK grant (SUPER is unconstrained).
        String groupId = createGroup(SUPER_UUID, "acme", "esc 팀 F");
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "seed grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPPORT_LOCK\"]}"))
                .andExpect(status().isCreated());

        // TENANT_ADMIN @ acme may manage this acme group (D3 in scope), but adding a member
        // would fan out SUPPORT_LOCK — a role it does not hold → the D4 re-check denies it.
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "bypass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + MEMBER_UUID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_GRANT_FORBIDDEN"));
    }

    private String createGroup(String actorUuid, String tenantId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(actorUuid))
                        .header("X-Operator-Reason", "create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenantId + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString()).get("groupId").asText();
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }
}
