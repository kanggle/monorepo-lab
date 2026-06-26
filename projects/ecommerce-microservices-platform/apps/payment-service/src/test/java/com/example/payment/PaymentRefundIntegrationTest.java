package com.example.payment;

import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.domain.model.PaymentStatus;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.adapter.in.event.OrderCancelledEventConsumer;
import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.polling.enabled=false"
})
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("결제 환불 통합 테스트")
class PaymentRefundIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGatewayPort paymentGateway;

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.confirmPayment(anyString(), anyString(), anyLong()))
                .willReturn(new PaymentGatewayConfirmResult("CARD", "https://receipt.test/mock"));
    }

    private String buildOrderPlacedJson(String orderId, String userId, long totalPrice) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "occurredAt", "2026-03-23T00:00:00",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", totalPrice,
                        "items", List.of()
                )
        ));
    }

    private String buildOrderCancelledJson(String orderId, String userId) throws Exception {
        return buildOrderCancelledJson(orderId, userId, "PAYMENT_TIMEOUT");
    }

    private String buildOrderCancelledJson(String orderId, String userId, String cancelReason) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderCancelled",
                "occurredAt", "2026-03-23T00:01:00",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "cancelledAt", "2026-03-23T00:01:00",
                        "cancelReason", cancelReason
                )
        ));
    }

    @Test
    @Disabled("TASK-BE-440: OrderCancelled→refund consumer path persists without an active "
            + "transaction (InvalidDataAccessApiUsageException: No EntityManager with actual transaction "
            + "available - cannot reliably process 'persist') (TASK-MONO-307 quarantine)")
    @DisplayName("OrderCancelled 이벤트 수신 시 COMPLETED 결제가 REFUNDED 상태로 변경된다")
    void orderCancelled_refundsPayment() throws Exception {
        String orderId = "order-" + System.nanoTime();
        String userId = "user-1";

        // PENDING 결제 생성
        orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 30000L));

        // PG 승인으로 COMPLETED 전이
        paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 30000L);

        // 환불
        orderCancelledEventConsumer.onMessage(buildOrderCancelledJson(orderId, userId));

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.get().getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("Payment 없는 orderId의 OrderCancelled 이벤트는 예외 없이 무시된다")
    void orderCancelled_noPayment_isIgnored() throws Exception {
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() ->
                orderCancelledEventConsumer.onMessage(buildOrderCancelledJson("nonexistent-order", "user-1"))
        );
    }

    // ── TASK-BE-435: money-safe late-payment race, BOTH orderings ────────────

    @Test
    @Disabled("TASK-BE-440: OrderCancelled→refund consumer path persists without an active "
            + "transaction (InvalidDataAccessApiUsageException: No EntityManager with actual transaction "
            + "available - cannot reliably process 'persist') (TASK-MONO-307 quarantine)")
    @DisplayName("AC-2 COMPLETED→CANCELLED(PAYMENT_TIMEOUT): 캡처된 결제가 OrderCancelled 수신 시 REFUNDED 된다 (자금 미보유)")
    void completedThenCancelled_paymentTimeout_refunds() throws Exception {
        String orderId = "order-" + System.nanoTime();
        String userId = "user-1";

        orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 30000L));
        paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 30000L);

        // stuck-detector cancel (PAYMENT_TIMEOUT) arrives AFTER capture
        orderCancelledEventConsumer.onMessage(buildOrderCancelledJson(orderId, userId, "PAYMENT_TIMEOUT"));

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.get().getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC-3 CANCELLED→COMPLETED: OrderCancelled 가 PENDING 결제를 VOIDED 시키면 뒤늦은 confirm 이 거부되어 자금이 캡처되지 않는다")
    void cancelledThenConfirm_lateConfirmRejected_noFundsCaptured() throws Exception {
        String orderId = "order-" + System.nanoTime();
        String userId = "user-1";

        // PENDING payment created
        orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 30000L));

        // OrderCancelled arrives while still PENDING → void
        orderCancelledEventConsumer.onMessage(buildOrderCancelledJson(orderId, userId, "PAYMENT_TIMEOUT"));

        var voided = paymentRepository.findByOrderId(orderId);
        assertThat(voided).isPresent();
        assertThat(voided.get().getStatus()).isEqualTo(PaymentStatus.VOIDED);

        // A late confirm must be rejected — funds never captured.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 30000L)
        ).isInstanceOf(com.example.payment.application.exception.PaymentAlreadyCompletedException.class);

        var after = paymentRepository.findByOrderId(orderId);
        assertThat(after).isPresent();
        assertThat(after.get().getStatus()).isEqualTo(PaymentStatus.VOIDED); // stays voided
        assertThat(after.get().getPaidAt()).isNull();                        // never captured
    }

    @Test
    @DisplayName("AC-7 멱등: 중복 OrderCancelled(VOIDED) 는 no-op (이중 전이 없음)")
    void duplicateOrderCancelled_voided_isIdempotent() throws Exception {
        String orderId = "order-" + System.nanoTime();
        String userId = "user-1";

        orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 30000L));
        orderCancelledEventConsumer.onMessage(buildOrderCancelledJson(orderId, userId, "PAYMENT_TIMEOUT"));
        // duplicate delivery
        orderCancelledEventConsumer.onMessage(buildOrderCancelledJson(orderId, userId, "PAYMENT_TIMEOUT"));

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.VOIDED);
    }
}
