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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-520 / ADR-MONO-046 D3 (AC-5) — tenant confinement: a {@code TENANT_ADMIN @ acme}
 * manages only acme's groups; another tenant's group is untouchable (403
 * {@code TENANT_SCOPE_DENIED}), and {@code SUPER_ADMIN} ({@code '*'}) is platform-wide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class GroupTenantScopeConfinementIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;
    static final ObjectMapper MAPPER = new ObjectMapper();

    static final String SUPER_UUID = "00000000-0000-7000-8000-00000be05211";
    static final String TA_ACME_UUID = "00000000-0000-7000-8000-00000be05212";

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
        GroupItSeeds.seedOperator(jdbcTemplate, SUPER_UUID, "*", "grpconf-super@example.com", "SUPER_ADMIN");
        GroupItSeeds.seedOperator(jdbcTemplate, TA_ACME_UUID, "acme", "grpconf-ta@example.com", "TENANT_ADMIN");
    }

    @Test
    @DisplayName("TENANT_ADMIN @ acme cannot mutate a globex group (403 TENANT_SCOPE_DENIED); SUPER_ADMIN can")
    void tenantAdmin_cannotTouchOtherTenantsGroup() throws Exception {
        String globexGroupId = createGroup(SUPER_UUID, "globex", "globex 지원팀");

        // TENANT_ADMIN @ acme has group.manage, but only scoped to acme → 403 on a globex group.
        mockMvc.perform(patch("/api/admin/groups/" + globexGroupId)
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "poke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"침입\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));

        // SUPER_ADMIN ('*') is platform-wide → the same PATCH succeeds.
        mockMvc.perform(patch("/api/admin/groups/" + globexGroupId)
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"globex 지원팀 v2\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TENANT_ADMIN @ acme may create an acme group but not a globex group (403 TENANT_SCOPE_DENIED)")
    void tenantAdmin_confinedToOwnTenantOnCreate() throws Exception {
        mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "own")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme\",\"name\":\"acme 자기팀\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(TA_ACME_UUID))
                        .header("X-Operator-Reason", "cross")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"globex\",\"name\":\"globex 침입팀\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
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
