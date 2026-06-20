package com.example.order;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.support.InternalJwtTestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for {@code POST /api/internal/orders/confirm-paid-stale}
 * (TASK-BE-412).
 *
 * <p>Drives the real {@code OrderSecurityConfig} resource-server chain +
 * {@code InternalOrderController} + {@code StalePaidOrderConfirmService} +
 * {@code StalePaidOrderConfirmHandler} (REQUIRES_NEW) + {@code OrderConfirmationService}
 * + {@code OrderRepository} (Postgres) + transactional outbox end-to-end. A local
 * MockWebServer serves the JWKS so the IT can mint a valid {@code client_credentials} JWT.
 *
 * <p>Asserts:
 * <ol>
 *   <li><b>Predicate disjointness (AC-3)</b>: a paid-unconfirmed PENDING order confirms;
 *       a {@code payment_id IS NULL} PENDING order (BE-138 bucket) is untouched; an
 *       already-CONFIRMED order is skipped.</li>
 *   <li><b>Saga-identical confirm (AC-4)</b>: an {@code OrderConfirmed} outbox row is
 *       written for the recovered order on topic {@code order.order.confirmed}.</li>
 *   <li><b>Auth fail-closed (AC-2)</b>: no/invalid bearer → 401; valid bearer → 200.</li>
 * </ol>
 */
@SpringBootTest(
        classes = com.example.order.OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "outbox.polling.interval-ms=600000",
                // Keep the BE-138 stuck-detector idle so it never races this test.
                "order.saga.stuck-detector.initial-delay-ms=86400000",
                "order.saga.stuck-detector.fixed-delay-ms=86400000"
        })
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("confirm-paid-stale 내부 엔드포인트 통합 테스트")
class ConfirmPaidStaleIT {

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
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate rest;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
    }

    private String url() {
        return "http://localhost:" + port + "/api/internal/orders/confirm-paid-stale";
    }

    // ---- AC-2: auth fail-closed ------------------------------------------------------

    @Test
    @DisplayName("Bearer 없음 → 401, sweep 미실행")
    void noBearer_returns401() {
        String paidStale = seedOrder("pay-1", OrderStatus.PENDING, 7200);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(url(), HttpMethod.POST,
                new HttpEntity<>("{\"olderThanMinutes\":30,\"limit\":200}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // sweep never ran — the order is still PENDING
        assertThat(orderRepository.findById(paidStale).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("잘못된 issuer 토큰 → 401, sweep 미실행")
    void wrongIssuerBearer_returns401() {
        seedOrder("pay-1", OrderStatus.PENDING, 7200);
        String badToken = jwt.issueToken("ecommerce-internal-services-client",
                "http://attacker", InternalJwtTestHelper.AUDIENCE, Duration.ofHours(1));

        ResponseEntity<String> response = post(badToken, "{\"olderThanMinutes\":30,\"limit\":200}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("잘못된 audience 토큰 → 401")
    void wrongAudienceBearer_returns401() {
        String badToken = jwt.issueToken("ecommerce-internal-services-client",
                InternalJwtTestHelper.ISSUER, "some-other-service", Duration.ofHours(1));

        ResponseEntity<String> response = post(badToken, "{\"olderThanMinutes\":30,\"limit\":200}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- AC-3 / AC-4 / AC-5: predicate disjointness + saga-identical confirm + skip --

    @Test
    @DisplayName("유효한 토큰 → 200; paid-unconfirmed 만 confirm, payment_id IS NULL 미선택, CONFIRMED skip, OrderConfirmed outbox 1행")
    void validBearer_confirmsOnlyPaidUnconfirmed() {
        String paidStale = seedOrder("pay-1", OrderStatus.PENDING, 7200);     // confirm
        String paymentNull = seedOrder(null, OrderStatus.PENDING, 7200);      // BE-138 bucket — untouched
        String alreadyConfirmed = seedOrder("pay-2", OrderStatus.CONFIRMED, 7200); // skipped

        ResponseEntity<Map> response = postForMap(jwt.validToken(),
                "{\"olderThanMinutes\":30,\"limit\":200}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        // Only the paid-unconfirmed order matches the predicate → scanned == 1.
        assertThat(((Number) body.get("scanned")).intValue()).isEqualTo(1);
        assertThat(((Number) body.get("confirmed")).intValue()).isEqualTo(1);
        assertThat(((Number) body.get("skipped")).intValue()).isZero();
        @SuppressWarnings("unchecked")
        List<String> confirmedIds = (List<String>) body.get("confirmedOrderIds");
        assertThat(confirmedIds).containsExactly(paidStale);

        // The recovered order is CONFIRMED.
        assertThat(orderRepository.findById(paidStale).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        // The payment_id IS NULL order (BE-138 bucket) is never selected → still PENDING.
        assertThat(orderRepository.findById(paymentNull).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        // The already-CONFIRMED order is untouched (it never matched the PENDING predicate).
        assertThat(orderRepository.findById(alreadyConfirmed).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);

        // AC-4: an OrderConfirmed outbox row is written for the recovered order only.
        assertThat(countOutboxRows("OrderConfirmed", paidStale)).isEqualTo(1);
        assertThat(countOutboxRows("OrderConfirmed", paymentNull)).isZero();
    }

    @Test
    @DisplayName("재실행 idempotent — 이미 confirm 된 주문은 다음 sweep 에서 미선택, scanned 0")
    void rerun_isNoOp() {
        seedOrder("pay-1", OrderStatus.PENDING, 7200);

        postForMap(jwt.validToken(), "{\"olderThanMinutes\":30,\"limit\":200}");
        ResponseEntity<Map> second = postForMap(jwt.validToken(),
                "{\"olderThanMinutes\":30,\"limit\":200}");

        Map<?, ?> body = second.getBody();
        assertThat(body).isNotNull();
        // After the first sweep confirmed it, the row no longer matches the PENDING predicate.
        assertThat(((Number) body.get("scanned")).intValue()).isZero();
        assertThat(((Number) body.get("confirmed")).intValue()).isZero();
    }

    @Test
    @DisplayName("올바른 토큰 + olderThanMinutes=0 → 400 INVALID_REQUEST")
    void invalidOlderThanMinutes_returns400() {
        ResponseEntity<String> response = post(jwt.validToken(),
                "{\"olderThanMinutes\":0,\"limit\":200}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_REQUEST");
    }

    @Test
    @DisplayName("올바른 토큰 + limit=2000 → 400 INVALID_REQUEST")
    void invalidLimit_returns400() {
        ResponseEntity<String> response = post(jwt.validToken(),
                "{\"olderThanMinutes\":30,\"limit\":2000}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_REQUEST");
    }

    // ---- helpers ---------------------------------------------------------------------

    private ResponseEntity<String> post(String bearer, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearer);
        return rest.exchange(url(), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> postForMap(String bearer, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearer);
        return rest.exchange(url(), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * Seeds a single order via JdbcTemplate so {@code created_at} can be pre-dated and
     * {@code payment_id} controlled directly. The aggregate refuses construction without
     * items, so the IT inserts the row directly (empty items is fine for this path).
     */
    private String seedOrder(String paymentId, OrderStatus status, long createdAtSecondsAgo) {
        String orderId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now().minusSeconds(createdAtSecondsAgo);
        Instant paidAt = paymentId != null ? createdAt : null;
        jdbc.update("INSERT INTO orders (order_id, user_id, status, total_price, " +
                        "recipient, phone, zip_code, address1, address2, " +
                        "created_at, updated_at, payment_id, paid_at, refunded_at, " +
                        "stuck_recovery_attempt_count, stuck_recovery_at, version) " +
                        "VALUES (?, ?, ?, 0, '홍길동', '010-0000-0000', '12345', " +
                        "'서울시 강남구', NULL, ?, ?, ?, ?, NULL, 0, NULL, 0)",
                orderId, "user-" + UUID.randomUUID(), status.name(),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt),
                paymentId,
                paidAt != null ? java.sql.Timestamp.from(paidAt) : null);
        return orderId;
    }

    private int countOutboxRows(String eventType, String orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT 1 FROM outbox WHERE event_type = ? AND aggregate_id = ?",
                eventType, orderId);
        return rows.size();
    }
}
