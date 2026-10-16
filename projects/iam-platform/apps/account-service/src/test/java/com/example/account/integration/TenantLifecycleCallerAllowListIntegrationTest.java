package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-640 — the tenant lifecycle API ({@code POST}/{@code GET /internal/tenants},
 * {@code GET}/{@code PATCH /internal/tenants/{tenantId}}) admits only its named callers, judged by the
 * token {@code sub} (= the {@code client_credentials} client id).
 *
 * <p>Contract: {@code specs/contracts/http/internal/admin-to-account.md} § Tenant lifecycle «호출자 규칙».
 * Writes + list: {@code admin-service-client}. Single read: that plus {@code auth-service-client}.
 * A refused caller gets {@code 403 FORBIDDEN} and the tenant row is unchanged.
 *
 * <p>Why this matters: in the demo account-service also sits on {@code traefik-net}, so a container on
 * that network holding any {@code internal.invoke} client could call these paths directly, around the
 * gateway that TASK-BE-638 closed. {@code product-service-client} (V0036) is the workload used as the
 * refused caller here — it legitimately holds {@code internal.invoke} for the tenant-scoped
 * sub-resources, which stay open to it (control below).
 *
 * <p>How the real chain is exercised: the class runs under the {@code test} profile (the dev bypass is
 * on, as for every account-service IT), and each request carries {@code Authorization: Bearer}. The
 * bearer filter replaces the bypass principal with a {@code JwtAuthenticationToken}, so the allow-list
 * judges the JWT. The decoder is mocked (no JWKS) — signature, issuer and scope have their own tests
 * ({@code SecurityConfigInternalValidatorTest}); this class measures only «which client».
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-BE-640 — 테넌트 라이프사이클 API 호출자 허용 목록 통합 테스트")
class TenantLifecycleCallerAllowListIntegrationTest extends AbstractIntegrationTest {

    private static final String PREFIX = "c640-";

    private static final String ADMIN = "tok-admin-service-client";
    private static final String AUTH = "tok-auth-service-client";
    private static final String PRODUCT = "tok-product-service-client";
    private static final String USER = "tok-user-uuid";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private AccountOutboxPublisher accountOutboxPublisher;
    // Replaces the real internalJwtDecoder: the token value names the client it was minted for.
    @MockitoBean private JwtDecoder internalJwtDecoder;

    @BeforeEach
    void stubTokens() {
        given(internalJwtDecoder.decode(ADMIN)).willReturn(workloadJwt(ADMIN, "admin-service-client"));
        given(internalJwtDecoder.decode(AUTH)).willReturn(workloadJwt(AUTH, "auth-service-client"));
        given(internalJwtDecoder.decode(PRODUCT)).willReturn(workloadJwt(PRODUCT, "product-service-client"));
        // A token whose sub is an account UUID (the shape of any identity-bearing token) — never a client id.
        given(internalJwtDecoder.decode(USER)).willReturn(workloadJwt(USER, UUID.randomUUID().toString()));
    }

    @AfterEach
    void deleteTenantsCreatedHere() {
        List<String> ids = jdbc.queryForList(
                "SELECT tenant_id FROM tenants WHERE tenant_id LIKE ?", String.class, PREFIX + "%");
        for (String id : ids) {
            jdbc.update("DELETE FROM tenant_domain_subscription WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", id);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static Jwt workloadJwt(String tokenValue, String subject) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject)
                .claim("scope", "internal.invoke")
                .claim("tenant_id", "global-account-platform")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String newTenantId() {
        return PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    private void seedTenant(String tenantId, String status) {
        jdbc.update("""
                INSERT INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'Caller Test Tenant', 'B2B_ENTERPRISE', ?, NOW(6), NOW(6))
                """, tenantId, status);
    }

    private String rowStatus(String tenantId) {
        return jdbc.queryForObject("SELECT status FROM tenants WHERE tenant_id = ?", String.class, tenantId);
    }

    private String rowDisplayName(String tenantId) {
        return jdbc.queryForObject("SELECT display_name FROM tenants WHERE tenant_id = ?", String.class, tenantId);
    }

    private int rowCount(String tenantId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE tenant_id = ?", Integer.class, tenantId);
        return n == null ? 0 : n;
    }

    private static String createBody(String tenantId) {
        return """
                {"tenantId": "%s", "displayName": "Caller Co", "tenantType": "B2B_ENTERPRISE"}
                """.formatted(tenantId);
    }

    // ── AC-1: a non-allowed internal.invoke token is refused, the row is unchanged ─────────────

    @Test
    @DisplayName("🔴 AC-1: product-service-client POST /internal/tenants → 403 FORBIDDEN · 행 0")
    void productClient_create_isRefused() throws Exception {
        String id = newTenantId();

        mockMvc.perform(post("/internal/tenants")
                        .header("Authorization", bearer(PRODUCT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(rowCount(id)).isZero();
    }

    @Test
    @DisplayName("🔴 AC-1: product-service-client GET /internal/tenants (전 테넌트 목록) → 403 FORBIDDEN")
    void productClient_list_isRefused() throws Exception {
        mockMvc.perform(get("/internal/tenants")
                        .header("Authorization", bearer(PRODUCT))
                        .param("size", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("🔴 AC-1: product-service-client PATCH SUSPENDED → ACTIVE (정지 해제) → 403 · 행 SUSPENDED 그대로")
    void productClient_reactivate_isRefused_rowUnchanged() throws Exception {
        String id = newTenantId();
        seedTenant(id, "SUSPENDED");

        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(PRODUCT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(rowStatus(id)).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("🔴 AC-1: product-service-client PATCH PENDING → ACTIVE (첫 관리자 게이트 우회) → 403 · 행 PENDING 그대로")
    void productClient_activatePending_isRefused_rowUnchanged() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");

        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(PRODUCT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\", \"displayName\": \"Hijacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(rowStatus(id)).isEqualTo("PENDING");
        assertThat(rowDisplayName(id)).isEqualTo("Caller Test Tenant");
    }

    @Test
    @DisplayName("🔴 AC-1: product-service-client GET /internal/tenants/{id} → 403 (단건 읽기는 admin · auth 만)")
    void productClient_getOne_isRefused() throws Exception {
        String id = newTenantId();
        seedTenant(id, "ACTIVE");

        mockMvc.perform(get("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(PRODUCT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("🔴 AC-1: sub 가 계정 UUID 인 internal.invoke 토큰 PATCH → 403 · 행 ACTIVE 그대로")
    void accountSubjectToken_patch_isRefused() throws Exception {
        String id = newTenantId();
        seedTenant(id, "ACTIVE");

        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isForbidden());

        assertThat(rowStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("🔴 auth-service-client 는 단건 읽기만 — POST · 목록 · PATCH → 403 · 행 그대로")
    void authClient_writesAndList_areRefused() throws Exception {
        String created = newTenantId();
        mockMvc.perform(post("/internal/tenants")
                        .header("Authorization", bearer(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(created)))
                .andExpect(status().isForbidden());
        assertThat(rowCount(created)).isZero();

        mockMvc.perform(get("/internal/tenants")
                        .header("Authorization", bearer(AUTH)))
                .andExpect(status().isForbidden());

        String id = newTenantId();
        seedTenant(id, "ACTIVE");
        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
        assertThat(rowStatus(id)).isEqualTo("ACTIVE");
    }

    // ── AC-2 (control): the named callers are unchanged ───────────────────────────────────────

    @Test
    @DisplayName("대조군 AC-2: admin-service-client — 생성(PENDING) → 첫 관리자 활성화 → 정지 → 재활성 → 단건 → 목록")
    void adminClient_fullLifecycle_unchanged() throws Exception {
        String id = newTenantId();

        mockMvc.perform(post("/internal/tenants")
                        .header("Authorization", bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId": "%s", "displayName": "Admin Co", "tenantType": "B2B_ENTERPRISE",
                                 "initialStatus": "PENDING"}
                                """.formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(rowStatus(id)).isEqualTo("PENDING");

        // first-admin activation (TASK-MONO-773): PENDING → ACTIVE
        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // suspend
        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(rowStatus(id)).isEqualTo("SUSPENDED");

        // reactivate
        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(rowStatus(id)).isEqualTo("ACTIVE");

        mockMvc.perform(get("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(id));

        String body = mockMvc.perform(get("/internal/tenants")
                        .header("Authorization", bearer(ADMIN))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].tenantId");
        assertThat(ids).isNotEmpty();
    }

    @Test
    @DisplayName("대조군 AC-2: auth-service-client GET /internal/tenants/{id} → 200 tenantType · status (로그인 · 가입 읽기)")
    void authClient_getOne_unchanged() throws Exception {
        String id = newTenantId();
        seedTenant(id, "ACTIVE");

        mockMvc.perform(get("/internal/tenants/{tenantId}", id)
                        .header("Authorization", bearer(AUTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantType").value("B2B_ENTERPRISE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("대조군: 규칙은 라이프사이클에만 — product-service-client 의 {id}/entitled-domains 서브리소스 → 200")
    void productClient_subResource_unchanged() throws Exception {
        String id = newTenantId();
        seedTenant(id, "ACTIVE");

        mockMvc.perform(get("/internal/tenants/{tenantId}/entitled-domains", id)
                        .header("Authorization", bearer(PRODUCT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(id));
    }

    @Test
    @DisplayName("대조군: 토큰 없는 test 프로필 bypass 는 그대로 통과 (다른 IT 가 기대는 경로)")
    void testProfileBypass_withoutToken_unchanged() throws Exception {
        String id = newTenantId();
        seedTenant(id, "ACTIVE");

        mockMvc.perform(get("/internal/tenants/{tenantId}", id))
                .andExpect(status().isOk());
    }
}
