package com.example.payment;

import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.example.payment.application.exception.IdempotencyKeyRequiredException;
import com.example.payment.application.exception.IdempotencyKeyConflictException;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.toss.TossPaymentsAdapter;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.application.service.PaymentRefundService;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * TASK-BE-535 ① — a duplicated PARTIAL refund must not pay out twice.
 *
 * <p>This is the <b>authoritative</b> lane: the mechanism is the Flyway V9 table plus its
 * {@code UNIQUE (payment_id, idempotency_key)} index, which only exists in a real
 * Postgres, and the property under test ({@code refundedAmount} does not accumulate
 * twice) is a persisted-state property. The unit tests pin the branch logic; this one
 * proves the migration applied and the constraint is real.
 *
 * <p>Every assertion is on the cumulative refunded <b>amount</b>, never on the existence
 * of a dedupe row — the row is the mechanism, the money is the property.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.polling.enabled=false"
})
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("부분 환불 멱등성 통합 테스트 (TASK-BE-535)")
class PaymentPartialRefundIdempotencyIntegrationTest {

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
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private TossPaymentsAdapter paymentGateway;

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.verify(any()))
                .willReturn(PaymentAuthorization.approved("pk_test", "CARD", "https://receipt.test/mock"));
    }

    /** Creates a COMPLETED payment of {@code amount} and returns its paymentId. */
    private String completedPayment(String userId, long amount) throws Exception {
        String orderId = "order-" + System.nanoTime();
        orderPlacedEventConsumer.onMessage(objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "occurredAt", "2026-07-20T00:00:00",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", amount,
                        "items", List.of()
                )
        )));
        paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, amount);
        return paymentRepository.findByOrderId(orderId).orElseThrow().getPaymentId();
    }

    private long refundedAmountOf(String paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow().getRefundedAmount();
    }

    /**
     * AC-1 — the same key replayed does NOT accumulate a second time. Without the guard
     * this leaves 20 000 refunded against a 30 000 payment: a real double payout.
     */
    @Test
    @DisplayName("AC-1 같은 키로 부분 환불 2회 → 누적 환불액이 두 번 증가하지 않는다")
    void sameKeyReplay_doesNotAccumulateTwice() throws Exception {
        String paymentId = completedPayment("user-1", 30000L);

        paymentRefundService.refundPayment(paymentId, "user-1", 10000L, "key-A");
        Payment afterReplay = paymentRefundService.refundPayment(paymentId, "user-1", 10000L, "key-A");

        assertThat(afterReplay.getRefundedAmount()).isEqualTo(10000L);
        assertThat(refundedAmountOf(paymentId)).isEqualTo(10000L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    /**
     * AC-2 — the regression guard (F1). A genuine second partial refund uses a different
     * key and must still succeed and accumulate.
     */
    @Test
    @DisplayName("AC-2 다른 키로 두 번째 부분 환불 → 성공하고 누적된다")
    void differentKey_isGenuineSecondRefund_andAccumulates() throws Exception {
        String paymentId = completedPayment("user-1", 30000L);

        paymentRefundService.refundPayment(paymentId, "user-1", 10000L, "key-A");
        paymentRefundService.refundPayment(paymentId, "user-1", 5000L, "key-B");

        assertThat(refundedAmountOf(paymentId)).isEqualTo(15000L);
    }

    /** Same key, different amount → 409-mapped exception; the first refund stands unchanged. */
    @Test
    @DisplayName("같은 키 + 다른 금액 → IdempotencyKeyConflictException, 누적액 불변")
    void sameKeyDifferentAmount_isRejected() throws Exception {
        String paymentId = completedPayment("user-1", 30000L);
        paymentRefundService.refundPayment(paymentId, "user-1", 10000L, "key-A");

        assertThatThrownBy(() ->
                paymentRefundService.refundPayment(paymentId, "user-1", 20000L, "key-A"))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(refundedAmountOf(paymentId)).isEqualTo(10000L);
    }

    /** The key is scoped to the payment — the same key value against another payment works. */
    @Test
    @DisplayName("키는 payment 단위 스코프 — 다른 payment 에 같은 키를 써도 정상 처리된다")
    void keyIsScopedToPayment() throws Exception {
        String first = completedPayment("user-1", 30000L);
        String second = completedPayment("user-1", 30000L);

        paymentRefundService.refundPayment(first, "user-1", 10000L, "shared-key");
        assertThatCode(() ->
                paymentRefundService.refundPayment(second, "user-1", 10000L, "shared-key"))
                .doesNotThrowAnyException();

        assertThat(refundedAmountOf(first)).isEqualTo(10000L);
        assertThat(refundedAmountOf(second)).isEqualTo(10000L);
    }

    /** Funds-out path refuses a keyless request outright — no partial refund is performed. */
    @Test
    @DisplayName("키 없는 부분 환불 요청은 거부되고 환불이 일어나지 않는다")
    void missingKey_isRefused_noRefundPerformed() throws Exception {
        String paymentId = completedPayment("user-1", 30000L);

        assertThatThrownBy(() ->
                paymentRefundService.refundPayment(paymentId, "user-1", 10000L, null))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        assertThat(refundedAmountOf(paymentId)).isZero();
    }

    /**
     * AC-5 backstop is a real DB constraint, not a read-then-write check. Asserted
     * directly against the schema so a future migration that drops or widens the index
     * fails here rather than silently re-opening the double-payout window.
     */
    @Test
    @DisplayName("AC-5 (payment_id, idempotency_key) 유니크 제약이 실제 스키마에 존재한다")
    void uniqueConstraintExistsInSchema() throws Exception {
        String paymentId = completedPayment("user-1", 30000L);
        paymentRefundService.refundPayment(paymentId, "user-1", 10000L, "key-A");

        // A raw duplicate insert bypassing the service must be rejected by the DB itself.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO payment_refund_request (payment_id, idempotency_key, amount) "
                        + "VALUES (?, ?, ?)", paymentId, "key-A", 10000L))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
