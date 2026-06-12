package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
import com.example.product.domain.event.ProductCreatedPayload;
import com.example.product.domain.event.ProductEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 — cross-tenant leak regression (multi-tenant.md M6, ADR-MONO-030 §2.3 AC-4).
 * Drives the full request path through {@code TenantContextFilter} (gateway
 * {@code X-Tenant-Id} header → request tenant context) so it proves M2 layer 2
 * (context propagation) + layer 3 (persistence {@code WHERE tenant_id}) together:
 * tenant A's product is invisible to tenant B (404, never 200 / 403 — M3 hides
 * existence), and a tenant-B write cannot reach a tenant-A row.
 *
 * <p>The Docker-free {@code :check} slice never loads the real wiring; this
 * Testcontainers {@code @SpringBootTest} is the authoritative isolation proof
 * (feedback_spring_boot_diagnostic_patterns §14-17). Cache is disabled so the test
 * exercises the DB-layer tenant filter deterministically (no Redis dependency);
 * the cache keys are independently tenant-prefixed in production code.
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@DisplayName("멀티테넌트 격리(M6) 통합 테스트 — cross-tenant leak 회귀")
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

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
        // Deterministic DB-layer isolation proof — no Redis container needed.
        registry.add("spring.cache.type", () -> "none");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String productBody(String name) {
        return """
                {
                  "name": "%s",
                  "description": "isolation fixture",
                  "price": 15000,
                  "variants": [ { "optionName": "기본", "stock": 10, "additionalPrice": 0 } ]
                }
                """.formatted(name);
    }

    private String registerProduct(String tenantId, String name) throws Exception {
        var request = post("/api/admin/products")
                .header(ROLE_HEADER, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productBody(name));
        // tenantId == null models the standalone/pre-multi-tenant path: no header
        // at all (MockMvc rejects a null header value, so omit it entirely).
        if (tenantId != null) {
            request = request.header(TENANT_HEADER, tenantId);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asText();
    }

    @Test
    @DisplayName("테넌트 A 상품을 테넌트 B 컨텍스트로 단건 조회하면 404 (A 컨텍스트로는 200)")
    void crossTenantDetailRead_returns404_sameTenantReturns200() throws Exception {
        String idA = registerProduct(TENANT_A, "테넌트A 상품");

        // tenant B cannot see tenant A's product — 404, not 403 (existence hidden, M3).
        mockMvc.perform(get("/api/products/{id}", idA).header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isNotFound());

        // tenant A sees its own product.
        mockMvc.perform(get("/api/products/{id}", idA).header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idA));
    }

    @Test
    @DisplayName("목록 조회는 자기 테넌트 상품만 포함한다")
    void list_isScopedToTenant() throws Exception {
        String idA = registerProduct(TENANT_A, "목록 격리 A");

        MvcResult bView = mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_B)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bView.getResponse().getContentAsString()).doesNotContain(idA);

        MvcResult aView = mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(aView.getResponse().getContentAsString()).contains(idA);
    }

    @Test
    @DisplayName("테넌트 B는 테넌트 A 상품을 변경할 수 없다 (404, A 데이터 불변)")
    void crossTenantWrite_cannotReachOtherTenantRow() throws Exception {
        String idA = registerProduct(TENANT_A, "변경 격리 A");

        // tenant B's update targets an id it cannot see → 404 (row unreachable).
        mockMvc.perform(patch("/api/admin/products/{id}", idA)
                        .header(ROLE_HEADER, "ADMIN")
                        .header(TENANT_HEADER, TENANT_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"탈취 시도\"}"))
                .andExpect(status().isNotFound());

        // tenant A's product is untouched.
        mockMvc.perform(get("/api/products/{id}", idA).header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("변경 격리 A"));
    }

    @Test
    @DisplayName("net-zero(D8): X-Tenant-Id 부재 = default 테넌트로 resolve")
    void noTenantHeader_resolvesToDefaultTenant() throws Exception {
        // standalone / pre-multi-tenant token — no header at all.
        String idDefault = registerProduct(null, "default 테넌트 상품");

        // visible without a header (default tenant)...
        mockMvc.perform(get("/api/products/{id}", idDefault))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idDefault));

        // ...but a real tenant (tenant-a) cannot see the default-tenant product.
        mockMvc.perform(get("/api/products/{id}", idDefault).header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이벤트 봉투에 요청 테넌트의 tenant_id가 실린다 (M5)")
    void publishedEvent_carriesRequestTenant() throws Exception {
        // The register path publishes ProductCreated; verify the envelope's tenant_id
        // matches the request tenant (publish-time context, not the default tenant).
        String id = registerProduct(TENANT_A, "이벤트 테넌트 검증");

        // kafkaTemplate is a context-shared mock — filter all captured created-events
        // down to the one we just produced (robust against other tests' publishes).
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(kafkaTemplate, org.mockito.Mockito.atLeastOnce())
                .send(org.mockito.ArgumentMatchers.eq("product.product.created"),
                        org.mockito.ArgumentMatchers.anyString(), captor.capture());

        ProductEvent created = captor.getAllValues().stream()
                .map(ProductEvent.class::cast)
                .filter(e -> e.payload() instanceof ProductCreatedPayload p && p.productId().equals(id))
                .findFirst()
                .orElseThrow();
        assertThat(created.tenantId()).isEqualTo(TENANT_A);
    }
}
