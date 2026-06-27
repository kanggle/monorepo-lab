package com.example.payment;

import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.example.payment.application.exception.PaymentAlreadyCompletedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.application.service.PaymentRefundService;
import com.example.payment.domain.model.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

/**
 * AC-2 (TASK-BE-437) durability invariant, exercised through the genuine VOIDED-during-capture
 * interleave it was written for (TASK-BE-443).
 *
 * <p><b>What this proves.</b> When {@code confirm()} captures funds for an order that an
 * {@code OrderCancelled} cancels <em>concurrently</em> — committing {@code VOIDED} on a separate
 * connection <em>during</em> the slow PG capture — and the post-capture auto-refund then FAILS at
 * the PG, the {@code PaymentRefundStranded} escalation outbox row (and the {@code stranded_refund}
 * obligation, BE-438), written by {@code PaymentRefundStrandedRecorder} in a {@code REQUIRES_NEW}
 * boundary, <b>survives</b> the {@code confirm()} {@code @Transactional} rollback.
 *
 * <p><b>Why this interleave is now reachable (TASK-BE-443).</b> {@code confirm()}'s pre-capture
 * read loads the payment as a MANAGED entity in its persistence context. Before BE-443 the
 * post-capture re-read used {@code findByOrderId}, which re-hydrates the matched row through the
 * same session — Hibernate's managed-entity identity returned the STALE PENDING instance and
 * discarded the freshly-read VOIDED columns, so the concurrent commit was invisible and the
 * stranded path was unreachable in a real DB (it only ever passed against a Mockito mock repo with
 * two distinct stubs). BE-443 switched the post-capture re-read to {@code findByOrderIdFresh}, which
 * forces an {@code entityManager.refresh} so the committed VOIDED is actually observed. This IT is
 * the integration proof of that fix.
 *
 * <p><b>Deterministic interleave (AC-2, no sleeps).</b> The interleave is gated on the
 * {@code confirmPayment} mock invocation: a Mockito {@code Answer} commits the {@code VOIDED}
 * transition on a SEPARATE physical connection via a {@code TransactionTemplate} with
 * {@code PROPAGATION_REQUIRES_NEW} (which suspends {@code confirm()}'s tx and commits independently)
 * BEFORE returning the successful capture. So {@code confirm()}'s pre-capture read deterministically
 * saw PENDING and its post-capture fresh re-read deterministically sees VOIDED — no wall-clock race.
 *
 * <p><b>Lane.</b> {@code @Tag("integration")}; runs on the ecommerce integration lane (TASK-MONO-307)
 * — not locally (Testcontainers Docker blocker on the Windows dev host, memory
 * {@code project_testcontainers_docker_desktop_blocker}). The Docker-free {@code :check} gate is the
 * unit suite in {@code PaymentConfirmServiceTest}.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "outbox.polling.enabled=false"
})
@Tag("integration")
@Testcontainers
@DisplayName("PaymentRefundStranded REQUIRES_NEW durability 통합 테스트 (TASK-BE-437 AC-2 / BE-443 interleave)")
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
    private PaymentConfirmService paymentConfirmService;

    @Autowired
    private PaymentRefundService paymentRefundService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Test
    @DisplayName("VOIDED-during-capture 인터리브: post-capture PG cancel 실패 시 confirm() 트랜잭션이 롤백되어도 "
            + "PaymentRefundStranded outbox row + stranded_refund obligation 은 살아남는다 (REQUIRES_NEW)")
    void strandedEscalation_survivesConfirmRollback() throws Exception {
        String orderId = "order-stranded-" + System.nanoTime();
        String userId = "user-stranded-" + System.nanoTime();

        // A REQUIRES_NEW template commits the concurrent VOIDED on a SEPARATE connection,
        // independently of confirm()'s parent tx (so it is NOT rolled back with it).
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Gate the interleave on the confirmPayment invocation (AC-2: no sleep). The Answer commits
        // VOIDED BEFORE returning the successful capture, so confirm()'s pre-capture read (already
        // done, saw PENDING) and its post-capture FRESH re-read (sees VOIDED) straddle the commit
        // deterministically. AtomicInteger guards against an unexpected second capture invocation.
        AtomicInteger captureCalls = new AtomicInteger();
        given(paymentGateway.confirmPayment(anyString(), anyString(), anyLong()))
                .willAnswer(invocation -> {
                    if (captureCalls.getAndIncrement() == 0) {
                        // Independent VOID commit on a separate connection, mid-capture.
                        requiresNew.executeWithoutResult(status ->
                                paymentRefundService.handleOrderCancelled(orderId));
                    }
                    return new PaymentGatewayConfirmResult("CARD", "https://receipt.test/mock");
                });

        // Post-capture auto-cancel (the refund of the just-captured amount) fails at the PG →
        // the existing strand trigger fires (PaymentConfirmService records the escalation in REQUIRES_NEW).
        doThrow(new PgGatewayUnavailableException("cancel retry exhausted"))
                .when(paymentGateway).cancelPayment(anyString(), eq("Order cancelled during confirm"));

        // PENDING payment created. NOTE: VOIDED is committed INSIDE the confirmPayment Answer during
        // confirm() below (NOT sequentially before it) — so confirm()'s pre-capture guard sees PENDING.
        orderPlacedEventConsumer.onMessage(orderPlacedJson(orderId, userId, 30000L));
        assertThat(paymentRepository.findByOrderId(orderId))
                .as("arrange: payment is PENDING before confirm so the pre-capture guard does NOT fire")
                .get()
                .extracting(p -> p.getStatus())
                .isEqualTo(PaymentStatus.PENDING);

        // confirm() captures, the Answer commits VOIDED mid-capture, the post-capture FRESH re-read
        // observes VOIDED, the auto-refund fails → escalates (REQUIRES_NEW) → rejects (parent rolls back).
        assertThatThrownBy(() ->
                paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 30000L))
                .isInstanceOf(PaymentAlreadyCompletedException.class);

        // The interleave really happened: PG capture ran exactly once, and the row is VOIDED — proving
        // the concurrent void committed independently and was the state the fresh re-read acted on
        // (not a vacuous pass via the pre-capture guard).
        assertThat(captureCalls.get()).as("PG capture invoked exactly once").isEqualTo(1);
        assertThat(paymentRepository.findByOrderId(orderId))
                .as("the concurrent VOID committed independently of the confirm() rollback")
                .get()
                .extracting(p -> p.getStatus())
                .isEqualTo(PaymentStatus.VOIDED);

        // The confirm() TX rolled back: no PaymentCompleted row.
        List<Map<String, Object>> completed = jdbcTemplate.queryForList(
                "SELECT * FROM payment_outbox WHERE event_type = 'PaymentCompleted' AND payload LIKE ?",
                "%" + orderId + "%");
        assertThat(completed).as("PaymentCompleted must NOT be written — confirm rolled back").isEmpty();

        // But the REQUIRES_NEW escalation committed independently and survives.
        List<Map<String, Object>> stranded = jdbcTemplate.queryForList(
                "SELECT * FROM payment_outbox WHERE event_type = 'PaymentRefundStranded' AND payload LIKE ?",
                "%" + orderId + "%");
        assertThat(stranded).as("PaymentRefundStranded must survive the confirm rollback").hasSize(1);
        assertThat(stranded.get(0).get("aggregate_type")).isEqualTo("Payment");

        JsonNode envelope = objectMapper.readTree((String) stranded.get(0).get("payload"));
        assertThat(envelope.get("event_type").asText()).isEqualTo("PaymentRefundStranded");
        assertThat(envelope.get("payload").get("orderId").asText()).isEqualTo(orderId);
        assertThat(envelope.get("payload").get("amount").asLong()).isEqualTo(30000L);
        assertThat(envelope.get("payload").get("reason").asText()).isEqualTo("PgGatewayUnavailableException");

        // BE-438: the matching durable stranded_refund obligation co-committed in the SAME
        // REQUIRES_NEW tx, exactly once (open, deduped per payment_id), and also survives the rollback.
        List<Map<String, Object>> obligations = jdbcTemplate.queryForList(
                "SELECT status, amount FROM stranded_refund WHERE order_id = ?", orderId);
        assertThat(obligations).as("exactly one open stranded_refund obligation survives").hasSize(1);
        assertThat(obligations.get(0).get("status")).isEqualTo("STRANDED");
        assertThat(((Number) obligations.get(0).get("amount")).longValue()).isEqualTo(30000L);
    }
}
