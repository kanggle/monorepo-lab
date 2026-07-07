package com.example.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order seller-scope ABAC isolation IT (ADR-MONO-030 Step 3 §3.3 / §6, AC-3/4/6, F1).
 * Drives the OPERATOR admin path through {@code TenantContextFilter} +
 * {@code SellerScopeContextFilter}: within one tenant, a seller-scoped operator only
 * sees orders that contain a line attributed to its seller; an unrestricted operator
 * (no scope / '*') sees every order (net-zero, fail-OPEN). A multi-seller order is
 * visible to each of its sellers. Lines carry their captured seller_id.
 *
 * <p>Sits ALONGSIDE the Step-2 M6 cross-tenant test (not modified). Pinned to
 * {@link OrderServiceApplication}. Excluded from the Docker-free {@code :check}.
 */
@SpringBootTest(
        classes = OrderServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 셀러-스코프 ABAC 격리(Step 3) 통합 테스트 — cross-seller + net-zero")
class SellerScopeIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String SELLER_SCOPE_HEADER = "X-Seller-Scope";
    private static final String USER_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String TENANT_A = "tenant-a";
    private static final String SELLER_A1 = "seller-a1";
    private static final String SELLER_A2 = "seller-a2";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** A single-seller order body. */
    private static String singleSellerBody(String sellerId) {
        return """
                {
                  "items": [
                    {"productId": "p1", "variantId": "v1", "productName": "노트북",
                     "quantity": 1, "unitPrice": 500000, "sellerId": "%s"}
                  ],
                  "shippingAddress": {
                    "recipient": "홍길동", "phone": "010-1234-5678",
                    "zipCode": "12345", "address1": "서울시 강남구"
                  }
                }
                """.formatted(sellerId);
    }

    /** A multi-seller order (lines for a1 + a2). */
    private static String multiSellerBody() {
        return """
                {
                  "items": [
                    {"productId": "p1", "variantId": "v1", "productName": "노트북",
                     "quantity": 1, "unitPrice": 500000, "sellerId": "%s"},
                    {"productId": "p2", "variantId": "v2", "productName": "마우스",
                     "quantity": 2, "unitPrice": 30000, "sellerId": "%s"}
                  ],
                  "shippingAddress": {
                    "recipient": "홍길동", "phone": "010-1234-5678",
                    "zipCode": "12345", "address1": "서울시 강남구"
                  }
                }
                """.formatted(SELLER_A1, SELLER_A2);
    }

    private String placeOrder(String userId, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/orders")
                .header(USER_HEADER, userId)
                .header(TENANT_HEADER, TENANT_A)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        String response = mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("orderId").asText();
    }

    @Test
    @DisplayName("셀러 a1 스코프 운영자 목록은 a1 라인을 가진 주문만 포함한다 (cross-seller, AC-6)")
    void adminList_sellerScoped_seesOwnOrdersOnly() throws Exception {
        String a1Order = placeOrder("user-a1-" + System.nanoTime(), singleSellerBody(SELLER_A1));
        String a2Order = placeOrder("user-a2-" + System.nanoTime(), singleSellerBody(SELLER_A2));

        String view = mockMvc.perform(get("/api/admin/orders")
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR")
                        .header(TENANT_HEADER, TENANT_A)
                        .header(SELLER_SCOPE_HEADER, SELLER_A1)
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(view).contains(a1Order).doesNotContain(a2Order);

        // a1-scoped detail of a2's order → 404 (existence hidden, M3).
        mockMvc.perform(get("/api/admin/orders/{id}", a2Order)
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR")
                        .header(TENANT_HEADER, TENANT_A)
                        .header(SELLER_SCOPE_HEADER, SELLER_A1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("다중 셀러 주문은 각 셀러 스코프 운영자에게 보이고 라인이 올바르게 귀속된다 (AC-3)")
    void multiSellerOrder_visibleToEachSeller_linesAttributed() throws Exception {
        String orderId = placeOrder("user-multi-" + System.nanoTime(), multiSellerBody());

        // visible to a1's scope — the full multi-seller order is returned (EXISTS predicate,
        // attribution not isolation) with BOTH lines carrying their captured seller_id. The
        // @OneToMany items collection has no @OrderBy, so the JSON array order is not
        // guaranteed by Postgres — assert both sellers are present order-independently (the
        // prior $.items[0]/$.items[1] index assertion was the drifted expectation).
        mockMvc.perform(get("/api/admin/orders/{id}", orderId)
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR").header(TENANT_HEADER, TENANT_A)
                        .header(SELLER_SCOPE_HEADER, SELLER_A1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].sellerId",
                        org.hamcrest.Matchers.containsInAnyOrder(SELLER_A1, SELLER_A2)));
        // ...and to a2's scope.
        mockMvc.perform(get("/api/admin/orders/{id}", orderId)
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR").header(TENANT_HEADER, TENANT_A)
                        .header(SELLER_SCOPE_HEADER, SELLER_A2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sellerId",
                        org.hamcrest.Matchers.containsInAnyOrder(SELLER_A1, SELLER_A2)));
    }

    @Test
    @DisplayName("스코프 부재/'*' 운영자 = 테넌트 전체 주문 (net-zero, F1)")
    void adminList_unrestricted_seesAllOrders() throws Exception {
        String a1Order = placeOrder("user-nz1-" + System.nanoTime(), singleSellerBody(SELLER_A1));
        String a2Order = placeOrder("user-nz2-" + System.nanoTime(), singleSellerBody(SELLER_A2));

        String noScope = mockMvc.perform(get("/api/admin/orders")
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR").header(TENANT_HEADER, TENANT_A).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(noScope).contains(a1Order).contains(a2Order);

        String wildcard = mockMvc.perform(get("/api/admin/orders")
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR").header(TENANT_HEADER, TENANT_A)
                        .header(SELLER_SCOPE_HEADER, "*").param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(wildcard).contains(a1Order).contains(a2Order);
    }
}
