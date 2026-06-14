package com.example.shipping;

import com.example.shipping.application.service.AutoCollectTrackingService;
import com.example.shipping.infrastructure.event.WmsShippingConfirmedConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 — shipping-service cross-tenant leak regression (ADR-MONO-030 §2.3 AC-4;
 * TASK-BE-369). Drives the full request path through {@code TenantContextFilter}
 * (gateway {@code X-Tenant-Id} header → request tenant context) so it proves M2 layer
 * 2 (context propagation) + M3 layer 3 (persistence {@code WHERE tenant_id}) together:
 * <ul>
 *   <li>(a) the admin list under tenant A excludes tenant B's shipment;</li>
 *   <li>(b) the admin {@code updateStatus} / {@code refreshTracking} mutation on tenant
 *       B's {@code shippingId} under tenant A context returns 404 (existence hidden, M3
 *       cross-tenant-read-is-not-found — not 403);</li>
 *   <li>(c) the SYSTEM paths (wms-confirm return leg {@code markShippedByOrderId} +
 *       the auto-collect sweep) are tenant-AGNOSTIC: they locate / process the row
 *       regardless of any request tenant, and the persisted row keeps its original
 *       tenant.</li>
 * </ul>
 *
 * <p>Shippings are seeded deterministically via {@link JdbcTemplate} with an explicit
 * {@code tenant_id} (no per-tenant HTTP create seam), then the admin assertions run
 * through the HTTP surface (tenant filter + persistence scoping end-to-end) and the
 * system assertions drive the consumer / sweep beans directly. The Docker-free
 * {@code :check} slice never loads the real wiring; this Testcontainers
 * {@code @SpringBootTest} is the authoritative isolation proof. Pinned to
 * {@link ShippingServiceApplication} so a bare-{@code @SpringBootTest} configuration
 * ambiguity can never bite.
 */
@SpringBootTest(
        classes = ShippingServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("멀티테넌트 격리(M6) 통합 테스트 — shipping cross-tenant leak 회귀")
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shipping_db")
            .withUsername("shipping_user")
            .withPassword("shipping_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WmsShippingConfirmedConsumer wmsShippingConfirmedConsumer;

    @Autowired
    private AutoCollectTrackingService autoCollectTrackingService;

    @Autowired
    private ObjectMapper objectMapper;

    /** Seeds a PREPARING shippings row with an explicit tenant_id; returns the shipping id. */
    private String seedPreparing(String tenantId, String orderId) {
        String shippingId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO shippings (shipping_id, tenant_id, order_id, user_id, status, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, 'PREPARING', ?, ?)",
                shippingId, tenantId, orderId, "user-" + orderId,
                Timestamp.from(now), Timestamp.from(now));
        return shippingId;
    }

    /** Seeds a SHIPPED shippings row with tracking + carrier (in-flight sweep subject). */
    private String seedShipped(String tenantId, String orderId, String tracking) {
        String shippingId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO shippings (shipping_id, tenant_id, order_id, user_id, status, "
                        + "tracking_number, carrier, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'SHIPPED', ?, 'CJ', ?, ?)",
                shippingId, tenantId, orderId, "user-" + orderId, tracking,
                Timestamp.from(now), Timestamp.from(now));
        return shippingId;
    }

    private String tenantOf(String shippingId) {
        return jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM shippings WHERE shipping_id = ?", String.class, shippingId);
    }

    @Test
    @DisplayName("(a) 관리자 목록 조회는 자기 테넌트 배송만 포함한다")
    void crossTenantAdminListIsScopedToTenant() throws Exception {
        String shippingIdA = seedPreparing(TENANT_A, "order-list-" + System.nanoTime());

        // tenant B's admin list does NOT contain tenant A's shipment.
        mockMvc.perform(get("/api/shippings")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(shippingIdA))));

        // tenant A's admin list DOES contain it.
        mockMvc.perform(get("/api/shippings")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(shippingIdA)));
    }

    @Test
    @DisplayName("(b) 테넌트 B 배송을 테넌트 A 컨텍스트로 상태변경하면 404 (B 컨텍스트로는 200)")
    void crossTenantUpdateStatus_returns404() throws Exception {
        String shippingIdB = seedPreparing(TENANT_B, "order-upd-" + System.nanoTime());
        String body = objectMapper.writeValueAsString(Map.of(
                "status", "SHIPPED", "trackingNumber", "TRK-1", "carrier", "CJ"));

        // tenant A cannot mutate tenant B's shipment — 404, not 403 (existence hidden, M3).
        mockMvc.perform(put("/api/shippings/{id}/status", shippingIdB)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPPING_NOT_FOUND"));

        // tenant B can mutate its own shipment.
        mockMvc.perform(put("/api/shippings/{id}/status", shippingIdB)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingId").value(shippingIdB))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("(b) 테넌트 B 배송을 테넌트 A 컨텍스트로 refresh-tracking 하면 404")
    void crossTenantRefreshTracking_returns404() throws Exception {
        String shippingIdB = seedShipped(TENANT_B, "order-ref-" + System.nanoTime(), "TRK-REF");

        mockMvc.perform(post("/api/shippings/{id}/refresh-tracking", shippingIdB)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPPING_NOT_FOUND"));

        // tenant B reaches its own shipment (200; best-effort no-op under mock carrier).
        mockMvc.perform(post("/api/shippings/{id}/refresh-tracking", shippingIdB)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingId").value(shippingIdB));
    }

    @Test
    @DisplayName("(c) system 경로(wms 확정 return leg)는 테넌트 무관하게 처리하고 row 테넌트는 보존된다")
    void systemReturnLeg_isTenantAgnostic_andPreservesRowTenant() throws Exception {
        String orderNo = "order-wms-" + System.nanoTime();
        String shippingIdB = seedPreparing(TENANT_B, orderNo);

        // The wms-confirm consumer runs with no HTTP tenant context; it locates the row by
        // the globally-unique orderNo regardless of tenant and flips PREPARING → SHIPPED.
        String wmsJson = objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "outbound.shipping.confirmed",
                "occurredAt", "2026-06-08T15:00:00Z",
                "aggregateType", "outbound",
                "aggregateId", "wms-internal-1",
                "payload", Map.of(
                        "orderId", "wms-internal-1",
                        "orderNo", orderNo,
                        "shipmentNo", "SHP-AGNOSTIC-1",
                        "carrierCode", "CJ-LOGISTICS",
                        "shippedAt", "2026-06-08T15:00:00Z")));

        wmsShippingConfirmedConsumer.onMessage(wmsJson);

        // The row advanced (agnostic) AND kept its original tenant (tenant_id updatable=false).
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM shippings WHERE shipping_id = ?", String.class, shippingIdB))
                .isEqualTo("SHIPPED");
        assertThat(tenantOf(shippingIdB)).isEqualTo(TENANT_B);
    }

    @Test
    @DisplayName("(c) auto-collect sweep는 테넌트 무관하게 모든 in-flight 배송을 본다")
    void autoCollectSweep_isTenantAgnostic() {
        // Two in-flight shipments in distinct tenants; the background sweep has no request
        // tenant context and must see BOTH (tenant-filtering would strand other-tenant rows).
        seedShipped(TENANT_A, "order-sweep-a-" + System.nanoTime(), "TRK-SWEEP-A");
        seedShipped(TENANT_B, "order-sweep-b-" + System.nanoTime(), "TRK-SWEEP-B");

        AutoCollectTrackingService.SweepResult result = autoCollectTrackingService.sweep();

        // Both rows were processed regardless of tenant (mock carrier → no-op advance, but
        // both are in the batch — proving the finder is not tenant-scoped).
        assertThat(result.processed()).isGreaterThanOrEqualTo(2);
    }
}
