package com.example.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 — order-service cross-tenant leak regression (multi-tenant.md M6,
 * ADR-MONO-030 §2.3 AC-4). Drives the full request path through
 * {@code TenantContextFilter} (gateway {@code X-Tenant-Id} header → request tenant
 * context) so it proves M2 layer 2 (context propagation) + layer 3 (persistence
 * {@code WHERE tenant_id}) together: tenant A's order is invisible to tenant B
 * (404, never 200 — M3 hides existence), a tenant-B write (cancel) cannot reach a
 * tenant-A row, the list is tenant-scoped, and the outbox event envelope carries
 * the request tenant (M5).
 *
 * <p>The Docker-free {@code :check} slice never loads the real wiring; this
 * Testcontainers {@code @SpringBootTest} is the authoritative isolation proof
 * (feedback_spring_boot_diagnostic_patterns §14-17). Pinned to
 * {@link OrderServiceApplication} so the bare-{@code @SpringBootTest} +
 * {@code TestOrderServiceApplication} multiple-{@code @SpringBootConfiguration}
 * ambiguity can never bite.
 */
@SpringBootTest(
        classes = OrderServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("멀티테넌트 격리(M6) 통합 테스트 — order cross-tenant leak 회귀")
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Id";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String placeBody() {
        return """
                {
                  "items": [
                    {"productId": "p1", "variantId": "v1", "productName": "노트북", "quantity": 1, "unitPrice": 500000}
                  ],
                  "shippingAddress": {
                    "recipient": "홍길동", "phone": "010-1234-5678",
                    "zipCode": "12345", "address1": "서울시 강남구"
                  }
                }
                """;
    }

    private String placeOrder(String tenantId, String userId) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/orders")
                .header(USER_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(placeBody());
        // tenantId == null models the standalone / pre-multi-tenant path: no header
        // at all (MockMvc rejects a null header value, so omit it entirely).
        if (tenantId != null) {
            request = request.header(TENANT_HEADER, tenantId);
        }
        String response = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("orderId").asText();
    }

    @Test
    @DisplayName("테넌트 A 주문을 테넌트 B 컨텍스트로 단건 조회하면 404 (A 컨텍스트로는 200)")
    void crossTenantDetailRead_returns404_sameTenantReturns200() throws Exception {
        String userId = "detail-user-" + System.nanoTime();
        String idA = placeOrder(TENANT_A, userId);

        // tenant B cannot see tenant A's order — 404, not 403 (existence hidden, M3).
        // The tenant filter resolves to empty before the userId-ownership check.
        mockMvc.perform(get("/api/orders/{orderId}", idA)
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isNotFound());

        // tenant A (same user) sees its own order.
        mockMvc.perform(get("/api/orders/{orderId}", idA)
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(idA));
    }

    @Test
    @DisplayName("주문 목록 조회는 자기 테넌트 주문만 포함한다")
    void list_isScopedToTenant() throws Exception {
        String userId = "list-user-" + System.nanoTime();
        String idA = placeOrder(TENANT_A, userId);

        String bView = mockMvc.perform(get("/api/orders")
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_B)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(bView).doesNotContain(idA);

        String aView = mockMvc.perform(get("/api/orders")
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(aView).contains(idA);
    }

    @Test
    @DisplayName("테넌트 B는 테넌트 A 주문을 취소할 수 없다 (404, A 데이터 불변)")
    void crossTenantWrite_cannotReachOtherTenantRow() throws Exception {
        String userId = "cancel-user-" + System.nanoTime();
        String idA = placeOrder(TENANT_A, userId);

        // tenant B's cancel targets an id it cannot see → 404 (row unreachable).
        mockMvc.perform(post("/api/orders/{orderId}/cancel", idA)
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isNotFound());

        // tenant A's order is untouched — still PENDING.
        mockMvc.perform(get("/api/orders/{orderId}", idA)
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("net-zero(D8): X-Tenant-Id 부재 = default 테넌트로 resolve")
    void noTenantHeader_resolvesToDefaultTenant() throws Exception {
        String userId = "default-user-" + System.nanoTime();
        // standalone / pre-multi-tenant token — no tenant header at all.
        String idDefault = placeOrder(null, userId);

        // visible without a header (default tenant)...
        mockMvc.perform(get("/api/orders/{orderId}", idDefault)
                        .header(USER_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(idDefault));

        // ...but a real tenant (tenant-a) cannot see the default-tenant order.
        mockMvc.perform(get("/api/orders/{orderId}", idDefault)
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("outbox 이벤트 봉투에 요청 테넌트의 tenant_id가 실린다 (M5)")
    void publishedEvent_carriesRequestTenant() throws Exception {
        String userId = "event-user-" + System.nanoTime();
        String idA = placeOrder(TENANT_A, userId);

        // The place path co-commits an OrderPlaced row to the outbox; the row (and
        // its payload) persist even after the poller marks it PUBLISHED.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT payload FROM order_outbox WHERE aggregate_id = ? AND event_type = 'OrderPlaced'", idA);
        assertThat(rows).hasSize(1);

        JsonNode envelope = objectMapper.readTree((String) rows.get(0).get("payload"));
        assertThat(envelope.get("tenant_id").asText()).isEqualTo(TENANT_A);
    }
}
