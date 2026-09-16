package com.example.order;

import com.example.order.domain.model.OrderStatus;
import com.example.order.support.InternalJwtTestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-INT-028 — {@code POST /api/internal/orders/existence} on a real Postgres through the real
 * {@code /api/internal/**} security chain.
 *
 * <p>The property under test is the money one: batch-worker releases the coupon of every order this
 * endpoint does not report, so an order in <b>another tenant</b> or in a <b>terminal status</b> must still
 * be reported. A tenant- or status-filtered query would pass a mocked unit test and free real coupons.
 */
@SpringBootTest(
        classes = com.example.order.OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "order.outbox.initial-delay-ms=600000",
                "order.saga.stuck-detector.initial-delay-ms=86400000",
                "order.saga.stuck-detector.fixed-delay-ms=86400000"
        })
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 존재 조회 내부 엔드포인트 통합 테스트 (TASK-INT-028)")
class OrderExistenceIT {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    static InternalJwtTestHelper jwt;

    static {
        try {
            jwt = InternalJwtTestHelper.start();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start JWKS MockWebServer", e);
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("order.internal.oauth2.jwk-set-uri", jwt::jwkSetUri);
        registry.add("order.internal.oauth2.issuer", () -> InternalJwtTestHelper.ISSUER);
        registry.add("order.internal.oauth2.audience", () -> InternalJwtTestHelper.AUDIENCE);
    }

    @AfterAll
    static void closeJwks() {
        if (jwt != null) {
            jwt.close();
        }
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate rest;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM order_outbox");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
    }

    private String url() {
        return "http://localhost:" + port + "/api/internal/orders/existence";
    }

    private String seedOrder(String tenantId, OrderStatus status) {
        String orderId = UUID.randomUUID().toString();
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO orders (order_id, user_id, tenant_id, status, total_price, " +
                        "recipient, phone, zip_code, address1, address2, " +
                        "created_at, updated_at, payment_id, paid_at, refunded_at, " +
                        "stuck_recovery_attempt_count, stuck_recovery_at, version) " +
                        "VALUES (?, ?, ?, ?, 0, '홍길동', '010-0000-0000', '12345', " +
                        "'서울시 강남구', NULL, ?, ?, NULL, NULL, NULL, 0, NULL, 0)",
                orderId, "user-" + UUID.randomUUID(), tenantId, status.name(), now, now);
        return orderId;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> post(String bearer, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(url(), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    @DisplayName("다른 테넌트의 주문과 CANCELLED 주문도 «존재» 로 답한다 — 없는 id 만 빠진다")
    void reportsOrdersInAnyTenantAndAnyStatus_andOmitsOnlyTheMissingOne() {
        String sameTenant = seedOrder("ecommerce", OrderStatus.PENDING);
        String otherTenant = seedOrder("other-tenant", OrderStatus.DELIVERED);
        String cancelled = seedOrder("ecommerce", OrderStatus.CANCELLED);
        String neverSaved = UUID.randomUUID().toString();

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = post(jwt.validToken(), "{\"orderIds\":[\"" + sameTenant + "\",\""
                + otherTenant + "\",\"" + cancelled + "\",\"" + neverSaved + "\"]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> existing = (List<String>) response.getBody().get("existingOrderIds");
        assertThat(existing).containsExactlyInAnyOrder(sameTenant, otherTenant, cancelled);
    }

    @Test
    @DisplayName("Bearer 없음 → 401")
    void noBearer_returns401() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = post(null, "{\"orderIds\":[\"x\"]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("빈 목록 → 400 INVALID_REQUEST")
    void emptyOrderIds_returns400() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = post(jwt.validToken(), "{\"orderIds\":[]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }
}
