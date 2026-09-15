package com.example.payment;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.toss.TossPaymentsAdapter;
import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.example.payment.application.exception.AmountMismatchException;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.domain.model.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

/**
 * The payment-service leg of TASK-INT-026, run against a real Spring context and database.
 *
 * <p>The defect sat between two services: the storefront charged the discounted amount while
 * payment-service built the PENDING payment from {@code OrderPlaced.totalPrice}. The web-store and
 * order-service suites each fix their own half; this test runs the half those suites cannot reach —
 * that payment-service really rejects the pre-fix amount pair and really accepts the post-fix one.
 *
 * <ul>
 *   <li><b>Before the fix</b> — {@code OrderPlaced.totalPrice} is the pre-discount 30,000 and the
 *       storefront confirms 25,000 → {@code AMOUNT_MISMATCH}, the payment stays PENDING, the PG is
 *       never asked. This is AC-0's "does it really fail" measurement.</li>
 *   <li><b>After the fix</b> — {@code OrderPlaced} carries the discounted {@code totalPrice} plus the
 *       additive {@code couponId} / {@code discountAmount}, and the storefront confirms that same
 *       amount → COMPLETED. This is AC-3's "the approval succeeds", and it also proves the consumer
 *       tolerates the two new payload fields.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.polling.enabled=false"
})
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("쿠폰 주문의 결제 금액 — 승인 비교 (TASK-INT-026)")
class CouponOrderPaymentAmountIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TossPaymentsAdapter paymentGateway;

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.verify(any()))
                .willReturn(PaymentAuthorization.approved("pk_test", "CARD", "https://receipt.test/mock"));
    }

    private String orderPlacedJson(String orderId, String userId, long totalPrice,
                                   String couponId, Long discountAmount) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("totalPrice", totalPrice);
        payload.put("items", List.of(Map.of(
                "productId", "p1", "variantId", "v1", "quantity", 1, "unitPrice", 30000L, "sellerId", "default")));
        if (couponId != null) {
            payload.put("couponId", couponId);
            payload.put("discountAmount", discountAmount);
        }
        return objectMapper.writeValueAsString(Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "OrderPlaced",
                "occurred_at", "2026-09-15T00:00:00Z",
                "source", "order-service",
                "payload", payload
        ));
    }

    @Test
    @DisplayName("🔴 수정 전 모양: PENDING 은 할인 전 30,000 인데 25,000 으로 승인하면 AMOUNT_MISMATCH — PG 는 불리지 않는다")
    void preFixShape_discountedConfirmAgainstUndiscountedPending_isAmountMismatch() throws Exception {
        String orderId = "order-coupon-prefix-" + System.nanoTime();
        String userId = "user-" + System.nanoTime();

        orderPlacedEventConsumer.onMessage(orderPlacedJson(orderId, userId, 30000L, null, null));

        assertThatThrownBy(() -> paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 25000L))
                .isInstanceOf(AmountMismatchException.class);

        var payment = paymentRepository.findByOrderId(orderId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getAmount()).isEqualTo(30000L);
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
        org.mockito.Mockito.verify(paymentGateway, never()).verify(any());
    }

    @Test
    @DisplayName("수정 후 모양: OrderPlaced 가 할인 후 25,000 과 쿠폰 필드를 실어 오면, 같은 금액의 승인이 COMPLETED 가 된다")
    void postFixShape_discountedTotalPrice_confirmSucceeds() throws Exception {
        String orderId = "order-coupon-postfix-" + System.nanoTime();
        String userId = "user-" + System.nanoTime();

        orderPlacedEventConsumer.onMessage(orderPlacedJson(orderId, userId, 25000L, "coupon-1", 5000L));

        var pending = paymentRepository.findByOrderId(orderId);
        assertThat(pending).isPresent();
        assertThat(pending.get().getAmount()).isEqualTo(25000L);
        assertThat(pending.get().getStatus()).isEqualTo(PaymentStatus.PENDING);

        paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 25000L);

        var completed = paymentRepository.findByOrderId(orderId);
        assertThat(completed).isPresent();
        assertThat(completed.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(completed.get().getAmount()).isEqualTo(25000L);
    }
}
