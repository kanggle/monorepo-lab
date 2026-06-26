package com.example.payment;

import com.example.payment.adapter.in.event.OrderCancelledEventConsumer;
import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.example.payment.application.exception.PaymentAlreadyCompletedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.service.PaymentConfirmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

/**
 * AC-2 (TASK-BE-437): the {@code PaymentRefundStranded} escalation outbox row must
 * <b>survive</b> the {@code confirm()} {@code @Transactional} rollback, because it is
 * written by {@code PaymentRefundStrandedRecorder} in a {@code REQUIRES_NEW} boundary
 * on a separate bean.
 *
 * <p>Scenario: a PENDING payment is captured during {@code confirm()}, an
 * {@code OrderCancelled} commits {@code VOIDED} concurrently (replayed here by the
 * cancel consumer before the post-capture re-read), and the post-capture PG
 * {@code cancelPayment} then throws {@code PgGatewayUnavailableException}. {@code confirm()}
 * re-throws {@code PaymentAlreadyCompletedException} → its TX rolls back → the
 * {@code PaymentCompleted} write (and everything else in that TX) is gone, but the
 * stranded escalation row, written in its own committed REQUIRES_NEW TX, remains.
 *
 * <p><b>Compile-only locally</b> (Testcontainers Docker blocker on this Windows host;
 * see memory {@code project_testcontainers_docker_desktop_blocker}) and there is no
 * ecommerce {@code @Tag("integration")} CI lane yet (TASK-MONO-307). The unit tests in
 * {@code PaymentConfirmServiceTest} are the CI gate; this IT documents the durability
 * proof for when the lane exists.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "outbox.polling.enabled=false"
})
@Tag("integration")
@Testcontainers
// TASK-BE-442: DISTINCT root cause from the BE-440 consumer-tx bug (which is now fixed — the two
// consumer-tx ITs in PaymentRefundIntegrationTest pass). This durability IT's arrange replays
// OrderPlaced then OrderCancelled SEQUENTIALLY, each onMessage committing its own tx. With the
// BE-440 fix the OrderCancelled now correctly COMMITS the payment to VOIDED *before* confirm()
// runs, so confirm()'s pre-capture guard (PaymentConfirmService line 46: status == VOIDED) fires
// and rejects BEFORE the PG capture — confirm() never reaches the post-capture auto-refund / stranded
// escalation path, so 0 PaymentRefundStranded rows are written. The post-capture stranded path is
// only reachable when VOIDED commits GENUINELY CONCURRENTLY (after confirm()'s initial read at
// line 35 but DURING the slow PG capture at line 58); the sequential arrange cannot model that
// interleave. Pre-fix it limped to the post-capture branch only because the VOIDED never committed.
// Fixing this needs concurrency injection in the test (commit VOIDED during the confirmPayment mock
// call) — separate test-design work that must not expand BE-440 scope nor risk the money-safety
// invariant. Production confirm() is correct in all orderings. Re-quarantined for TASK-BE-442.
@Disabled("TASK-BE-442: durability IT's sequential arrange commits VOIDED before confirm() so the "
        + "pre-capture guard rejects before capture — the post-capture stranded path needs a "
        + "genuinely-concurrent VOIDED-during-PG-capture interleave injected; distinct from BE-440")
@DisplayName("PaymentRefundStranded REQUIRES_NEW durability 통합 테스트 (TASK-BE-437 AC-2)")
class PaymentRefundStrandedDurabilityIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("payment_user")
            .withPassword("payment_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderPlacedEventConsumer orderPlacedEventConsumer;

    @Autowired
    private OrderCancelledEventConsumer orderCancelledEventConsumer;

    @Autowired
    private PaymentConfirmService paymentConfirmService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGatewayPort paymentGateway;

    private String orderPlacedJson(String orderId, String userId, long totalPrice) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "occurredAt", "2026-06-25T00:00:00",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", totalPrice,
                        "items", List.of()
                )
        ));
    }

    private String orderCancelledJson(String orderId, String userId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderCancelled",
                "occurredAt", "2026-06-25T00:01:00",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "cancelReason", "OPERATOR"
                )
        ));
    }

    @Test
    @DisplayName("post-capture PG cancel 실패 시 confirm() 트랜잭션이 롤백되어도 PaymentRefundStranded outbox row 는 살아남는다 (REQUIRES_NEW)")
    void strandedEscalation_survivesConfirmRollback() throws Exception {
        String orderId = "order-stranded-" + System.nanoTime();
        String userId = "user-stranded-" + System.nanoTime();

        // capture succeeds; post-capture auto-cancel fails at the PG.
        given(paymentGateway.confirmPayment(anyString(), anyString(), anyLong()))
                .willReturn(new PaymentGatewayConfirmResult("CARD", "https://receipt.test/mock"));
        doThrow(new PgGatewayUnavailableException("cancel retry exhausted"))
                .when(paymentGateway).cancelPayment(anyString(), eq("Order cancelled during confirm"));

        // PENDING payment created, then OrderCancelled commits VOIDED before the confirm re-read.
        orderPlacedEventConsumer.onMessage(orderPlacedJson(orderId, userId, 30000L));
        orderCancelledEventConsumer.onMessage(orderCancelledJson(orderId, userId));

        // confirm() captures, re-reads VOIDED, fails to cancel → escalates → rejects (rolls back).
        assertThatThrownBy(() ->
                paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 30000L))
                .isInstanceOf(PaymentAlreadyCompletedException.class);

        // The confirm() TX rolled back: no PaymentCompleted row.
        List<Map<String, Object>> completed = jdbcTemplate.queryForList(
                "SELECT * FROM outbox WHERE event_type = 'PaymentCompleted' AND payload LIKE ?",
                "%" + orderId + "%");
        assertThat(completed).as("PaymentCompleted must NOT be written — confirm rolled back").isEmpty();

        // But the REQUIRES_NEW escalation committed independently and survives.
        List<Map<String, Object>> stranded = jdbcTemplate.queryForList(
                "SELECT * FROM outbox WHERE event_type = 'PaymentRefundStranded' AND payload LIKE ?",
                "%" + orderId + "%");
        assertThat(stranded).as("PaymentRefundStranded must survive the confirm rollback").hasSize(1);
        assertThat(stranded.get(0).get("aggregate_type")).isEqualTo("Payment");

        JsonNode envelope = objectMapper.readTree((String) stranded.get(0).get("payload"));
        assertThat(envelope.get("event_type").asText()).isEqualTo("PaymentRefundStranded");
        assertThat(envelope.get("payload").get("orderId").asText()).isEqualTo(orderId);
        assertThat(envelope.get("payload").get("amount").asLong()).isEqualTo(30000L);
        assertThat(envelope.get("payload").get("reason").asText()).isEqualTo("PgGatewayUnavailableException");
    }
}
