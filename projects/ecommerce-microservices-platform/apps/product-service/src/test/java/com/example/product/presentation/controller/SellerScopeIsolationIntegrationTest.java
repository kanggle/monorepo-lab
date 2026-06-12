package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seller-scope ABAC isolation IT (ADR-MONO-030 Step 3 §3.3 / §6, AC-4/5/6, F1/F5).
 * Drives the full request path through {@code TenantContextFilter} +
 * {@code SellerScopeContextFilter} (gateway {@code X-Tenant-Id} / {@code X-Seller-Scope}
 * headers → request context) so it proves the composite isolate-then-attribute scope:
 * within ONE tenant, a seller-scoped operator (seller a1) sees only its own products,
 * never a2's; an unrestricted operator (no scope / '*') and the CONSUMER plane see the
 * full shared catalog (net-zero, fail-OPEN); the default-seller migration applies and a
 * no-seller product is owned by the default seller (D8).
 *
 * <p>Sits ALONGSIDE the Step-2 M6 cross-tenant test (it is not modified). Cache off
 * so the DB-layer filter is exercised deterministically. Excluded from the Docker-free
 * {@code :check} (no {@code -PrunIntegration}); the gate is unit+slice.
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@DisplayName("셀러-스코프 ABAC 격리(Step 3) 통합 테스트 — cross-seller + net-zero")
class SellerScopeIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String SELLER_SCOPE_HEADER = "X-Seller-Scope";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String TENANT_A = "tenant-a";
    private static final String SELLER_A1 = "seller-a1";
    private static final String SELLER_A2 = "seller-a2";

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String productBody(String name, String sellerId) {
        String sellerLine = sellerId == null ? "" : "\"sellerId\": \"" + sellerId + "\",";
        return """
                {
                  "name": "%s",
                  "description": "seller fixture",
                  "price": 15000,
                  %s
                  "variants": [ { "optionName": "기본", "stock": 10, "additionalPrice": 0 } ]
                }
                """.formatted(name, sellerLine);
    }

    private String registerProduct(String tenantId, String sellerId, String name) throws Exception {
        var request = post("/api/admin/products")
                .header(ROLE_HEADER, "ADMIN")
                .header(TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productBody(name, sellerId));
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("셀러 a1 스코프 운영자는 a1 상품만 보고 a2 상품은 404 (cross-seller, AC-6)")
    void sellerScoped_seesOwnOnly_crossSeller404() throws Exception {
        String a1 = registerProduct(TENANT_A, SELLER_A1, "a1 상품");
        String a2 = registerProduct(TENANT_A, SELLER_A2, "a2 상품");

        // a1-scoped detail of a2's product → 404 (existence hidden, M3).
        mockMvc.perform(get("/api/products/{id}", a2)
                        .header(TENANT_HEADER, TENANT_A).header(SELLER_SCOPE_HEADER, SELLER_A1))
                .andExpect(status().isNotFound());
        // a1-scoped detail of its own product → 200.
        mockMvc.perform(get("/api/products/{id}", a1)
                        .header(TENANT_HEADER, TENANT_A).header(SELLER_SCOPE_HEADER, SELLER_A1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(SELLER_A1));

        // a1-scoped list contains only a1's product.
        MvcResult list = mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_A).header(SELLER_SCOPE_HEADER, SELLER_A1)
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        String body = list.getResponse().getContentAsString();
        assertThat(body).contains(a1).doesNotContain(a2);
    }

    @Test
    @DisplayName("스코프 부재/'*' 운영자 + CONSUMER = 공유 카탈로그 전체 (net-zero, F1/F5)")
    void unrestricted_seesWholeCatalog() throws Exception {
        String a1 = registerProduct(TENANT_A, SELLER_A1, "netzero a1");
        String a2 = registerProduct(TENANT_A, SELLER_A2, "netzero a2");

        // no seller scope → both visible (fail-OPEN).
        MvcResult noScope = mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_A).param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        assertThat(noScope.getResponse().getContentAsString()).contains(a1).contains(a2);

        // wildcard '*' → both visible (not restricted).
        MvcResult wildcard = mockMvc.perform(get("/api/products")
                        .header(TENANT_HEADER, TENANT_A).header(SELLER_SCOPE_HEADER, "*").param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        assertThat(wildcard.getResponse().getContentAsString()).contains(a1).contains(a2);

        // CONSUMER detail (no scope header) of a2 → 200 (shared catalog, seller_id read-only).
        mockMvc.perform(get("/api/products/{id}", a2).header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(SELLER_A2));
    }

    @Test
    @DisplayName("default-seller 마이그레이션: seller 미지정 상품은 default seller 소유 (D8, AC-2/5)")
    void noSeller_ownedByDefaultSeller() throws Exception {
        String id = registerProduct(TENANT_A, null, "no seller 상품");

        JsonNode node = objectMapper.readTree(
                mockMvc.perform(get("/api/products/{id}", id).header(TENANT_HEADER, TENANT_A))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(node.get("sellerId").asText()).isEqualTo("default");
    }
}
