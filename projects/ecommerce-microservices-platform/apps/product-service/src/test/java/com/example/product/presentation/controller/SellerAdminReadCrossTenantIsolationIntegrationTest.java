package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seller admin read-surface cross-tenant isolation IT (TASK-BE-375, ADR-MONO-030
 * Step 4 facet f / Step 3 §6 M6). Seeds sellers in two tenants via the OPERATOR
 * register endpoint, then asserts the new read surface is tenant-scoped:
 * <ol>
 *   <li>{@code GET /api/admin/sellers} under tenant A lists only A's sellers,
 *       never B's (repository {@code WHERE tenant_id} chokepoint, no leak);</li>
 *   <li>{@code GET /api/admin/sellers/{id}} for tenant B's seller under tenant A
 *       context → 404 (existence hidden, M3 — not 403).</li>
 * </ol>
 *
 * <p>Sits ALONGSIDE the Step-3 seller-scope IT (not modified). Excluded from the
 * Docker-free {@code :check} ({@code @Tag("integration")}, no {@code -PrunIntegration});
 * CI-Linux authority. Cache off so DB-layer tenant filtering is exercised directly.
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@DisplayName("셀러 admin 읽기 표면 cross-tenant 격리(Step 4 facet f) 통합 테스트")
class SellerAdminReadCrossTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String SELLER_A = "seller-a-only";
    private static final String SELLER_B = "seller-b-only";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("product_user")
            .withPassword("product_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        registry.add("spring.cache.type", () -> "none");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * The outbound IAM provisioner is mocked (no real account-service in the IT). This
     * read-surface test only cares that sellers are persisted + tenant-scoped; the
     * onboarding provisioning attempt (ADR-042) is irrelevant here and fail-soft anyway.
     */
    @MockitoBean
    @SuppressWarnings("unused")
    private com.example.product.application.port.SellerAccountProvisioner sellerAccountProvisioner;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Stub the (bean-overridden) IAM provisioner so the seller-register flow gets a
     * concrete {@link ProvisioningResult} instead of an unstubbed mock's {@code null}
     * (which NPEs the onboarding path → HTTP 500). {@code failed()} keeps it fail-soft:
     * the seller is still persisted (PENDING_PROVISIONING) and the POST returns 201 —
     * all this read-surface test needs. No account/identity ids are stored, so the two
     * sellers cannot collide on a unique constraint (TASK-MONO-319).
     */
    @BeforeEach
    void stubProvisioner() {
        given(sellerAccountProvisioner.provision(anyString(), anyString(), anyString()))
                .willReturn(ProvisioningResult.failed());
    }

    private void registerSeller(String tenantId, String sellerId, String displayName) throws Exception {
        String body = """
                { "sellerId": "%s", "displayName": "%s" }
                """.formatted(sellerId, displayName);
        mockMvc.perform(post("/api/admin/sellers")
                        .header(ROLE_HEADER, "ADMIN")
                        .header(TENANT_HEADER, tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("(a) tenant A 목록은 A 셀러만 포함하고 B 셀러는 제외 / (b) B 셀러 detail 은 A 컨텍스트에서 404")
    void adminRead_isTenantScoped() throws Exception {
        registerSeller(TENANT_A, SELLER_A, "A 전용 셀러");
        registerSeller(TENANT_B, SELLER_B, "B 전용 셀러");

        // (a) list under tenant A contains A's seller, never B's.
        MvcResult list = mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "ADMIN")
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        String body = list.getResponse().getContentAsString();
        assertThat(body).contains(SELLER_A).doesNotContain(SELLER_B);

        // (b) tenant B's seller, queried under tenant A → 404 (existence hidden, M3).
        mockMvc.perform(get("/api/admin/sellers/{id}", SELLER_B)
                        .header(ROLE_HEADER, "ADMIN")
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound());

        // sanity: tenant A's own seller resolves under tenant A.
        mockMvc.perform(get("/api/admin/sellers/{id}", SELLER_A)
                        .header(ROLE_HEADER, "ADMIN")
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk());
    }
}
