package com.example.payment.domain.model;

import com.example.payment.domain.exception.InvalidPaymentException;
import com.example.payment.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment 애그리거트 단위 테스트")
class PaymentTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create 호출 시 PENDING 상태로 생성된다")
    void create_returnsPendingPayment() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        assertThat(payment.getPaymentId()).isNotNull();
        assertThat(payment.getOrderId()).isEqualTo("order-1");
        assertThat(payment.getUserId()).isEqualTo("user-1");
        assertThat(payment.getAmount()).isEqualTo(30000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getRefundedAt()).isNull();
        assertThat(payment.getPaymentKey()).isNull();
        assertThat(payment.getPaymentMethod()).isNull();
        assertThat(payment.getReceiptUrl()).isNull();
    }

    @Test
    @DisplayName("create 시 TenantContext 미설정이면 기본 테넌트 'ecommerce'가 할당된다 (D8 net-zero)")
    void create_noTenantContext_usesDefaultTenant() {
        TenantContext.clear();
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        assertThat(payment.getTenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("create 시 TenantContext 가 설정되면 해당 tenantId 가 할당된다")
    void create_withTenantContext_usesContextTenant() {
        TenantContext.set("tenant-a");
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        assertThat(payment.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    @DisplayName("PENDING 상태에서 confirm 호출 시 COMPLETED로 전이되고 PG 필드가 설정된다")
    void confirm_pendingPayment_becomesCompletedWithPgFields() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        payment.confirm("pk_test_123", "CARD", "https://receipt.url");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getPaymentKey()).isEqualTo("pk_test_123");
        assertThat(payment.getPaymentMethod()).isEqualTo("CARD");
        assertThat(payment.getReceiptUrl()).isEqualTo("https://receipt.url");
    }

    @Test
    @DisplayName("COMPLETED 상태에서 confirm 호출 시 예외가 발생한다")
    void confirm_completedPayment_throwsException() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("pk_test_123", "CARD", null);

        assertThatThrownBy(() -> payment.confirm("pk_test_456", "CARD", null))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    @DisplayName("PENDING 상태에서 fail 호출 시 FAILED로 전이된다")
    void fail_pendingPayment_becomesFailed() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("COMPLETED 상태에서 fail 호출 시 예외가 발생한다")
    void fail_completedPayment_throwsException() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("pk_test_123", "CARD", null);

        assertThatThrownBy(payment::fail)
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    @DisplayName("FAILED 상태에서 confirm 호출 시 예외가 발생한다")
    void confirm_failedPayment_throwsException() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.fail();

        assertThatThrownBy(() -> payment.confirm("pk_test_123", "CARD", null))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    @DisplayName("PENDING 상태에서 confirm 호출 시 COMPLETED로 전이된다")
    void complete_pendingPayment_becomesCompleted() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        payment.confirm("test-payment-key", "CARD", "http://receipt.example.com");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("COMPLETED 상태에서 confirm 재호출 시 예외가 발생한다")
    void complete_alreadyCompleted_isIdempotent() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("test-payment-key", "CARD", "http://receipt.example.com");

        assertThatThrownBy(() -> payment.confirm("test-payment-key", "CARD", "http://receipt.example.com"))
                .isInstanceOf(InvalidPaymentException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("REFUNDED 상태에서 confirm 호출 시 예외가 발생한다")
    void complete_refundedPayment_throwsInvalidPaymentException() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("test-payment-key", "CARD", "http://receipt.example.com");
        payment.refund();

        assertThatThrownBy(() -> payment.confirm("test-payment-key", "CARD", "http://receipt.example.com"))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    @DisplayName("COMPLETED 상태에서 refund 호출 시 REFUNDED로 전이된다")
    void refund_completedPayment_becomesRefunded() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("pk_test_123", "CARD", null);

        payment.refund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("REFUNDED 상태에서 refund 호출 시 멱등 처리된다")
    void refund_alreadyRefunded_isIdempotent() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("pk_test_123", "CARD", null);
        payment.refund();
        LocalDateTime firstRefundedAt = payment.getRefundedAt();

        assertThatNoException().isThrownBy(payment::refund);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isEqualTo(firstRefundedAt);
    }

    @Test
    @DisplayName("PENDING 상태에서 refund 호출 시 예외가 발생한다")
    void refund_pendingPayment_throwsInvalidPaymentException() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        assertThatThrownBy(payment::refund)
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    @DisplayName("reconstitute로 복원된 Payment는 모든 필드를 유지한다 (tenantId 포함)")
    void reconstitute_restoresAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = Payment.reconstitute(
                "pay-1", "order-1", "user-1", "tenant-a", 50000L,
                PaymentStatus.COMPLETED, now, now.plusMinutes(1), null,
                "pk_test_123", "CARD", "https://receipt.url"
        );

        assertThat(payment.getPaymentId()).isEqualTo("pay-1");
        assertThat(payment.getOrderId()).isEqualTo("order-1");
        assertThat(payment.getUserId()).isEqualTo("user-1");
        assertThat(payment.getTenantId()).isEqualTo("tenant-a");
        assertThat(payment.getAmount()).isEqualTo(50000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getCreatedAt()).isEqualTo(now);
        assertThat(payment.getPaidAt()).isEqualTo(now.plusMinutes(1));
        assertThat(payment.getRefundedAt()).isNull();
        assertThat(payment.getPaymentKey()).isEqualTo("pk_test_123");
        assertThat(payment.getPaymentMethod()).isEqualTo("CARD");
        assertThat(payment.getReceiptUrl()).isEqualTo("https://receipt.url");
    }

    @Test
    @DisplayName("동일 userId이면 isOwnedBy가 true를 반환한다")
    void isOwnedBy_sameUser_returnsTrue() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        assertThat(payment.isOwnedBy("user-1")).isTrue();
    }

    @Test
    @DisplayName("다른 userId이면 isOwnedBy가 false를 반환한다")
    void isOwnedBy_differentUser_returnsFalse() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);

        assertThat(payment.isOwnedBy("user-2")).isFalse();
    }

    @Test
    @DisplayName("도메인 모델에 JPA 의존성이 없다")
    void domainModel_hasNoJpaDependency() {
        Class<Payment> clazz = Payment.class;

        assertThat(clazz.getAnnotations())
                .noneMatch(a -> a.annotationType().getPackageName().startsWith("jakarta.persistence"));
    }
}
