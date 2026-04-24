package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentPersistenceMapper 단위 테스트")
class PaymentPersistenceMapperTest {

    private final PaymentPersistenceMapper mapper = new PaymentPersistenceMapper();

    @Test
    @DisplayName("도메인 -> JpaEntity 변환 시 모든 필드가 매핑된다")
    void toEntity_mapsAllFields() {
        Payment payment = Payment.reconstitute(
                "pay-1", "order-1", "user-1", 50000L,
                PaymentStatus.COMPLETED,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 5),
                null,
                "pk_test_123", "CARD", "https://receipt.url"
        );

        PaymentJpaEntity entity = mapper.toEntity(payment);

        assertThat(entity.getPaymentId()).isEqualTo("pay-1");
        assertThat(entity.getOrderId()).isEqualTo("order-1");
        assertThat(entity.getUserId()).isEqualTo("user-1");
        assertThat(entity.getAmount()).isEqualTo(50000L);
        assertThat(entity.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(entity.getCreatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 0));
        assertThat(entity.getPaidAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 5));
        assertThat(entity.getRefundedAt()).isNull();
        assertThat(entity.getPaymentKey()).isEqualTo("pk_test_123");
        assertThat(entity.getPaymentMethod()).isEqualTo("CARD");
        assertThat(entity.getReceiptUrl()).isEqualTo("https://receipt.url");
    }

    @Test
    @DisplayName("JpaEntity -> 도메인 변환 시 모든 필드가 매핑된다")
    void toDomain_mapsAllFields() {
        Payment original = Payment.reconstitute(
                "pay-1", "order-1", "user-1", 30000L,
                PaymentStatus.REFUNDED,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 5),
                LocalDateTime.of(2025, 1, 2, 9, 0),
                "pk_test_456", "TRANSFER", null
        );
        PaymentJpaEntity entity = mapper.toEntity(original);

        Payment restored = mapper.toDomain(entity);

        assertThat(restored.getPaymentId()).isEqualTo("pay-1");
        assertThat(restored.getOrderId()).isEqualTo("order-1");
        assertThat(restored.getUserId()).isEqualTo("user-1");
        assertThat(restored.getAmount()).isEqualTo(30000L);
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(restored.getCreatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 0));
        assertThat(restored.getPaidAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 5));
        assertThat(restored.getRefundedAt()).isEqualTo(LocalDateTime.of(2025, 1, 2, 9, 0));
        assertThat(restored.getPaymentKey()).isEqualTo("pk_test_456");
        assertThat(restored.getPaymentMethod()).isEqualTo("TRANSFER");
        assertThat(restored.getReceiptUrl()).isNull();
    }

    @Test
    @DisplayName("도메인 -> JpaEntity -> 도메인 왕복 변환 시 데이터 손실이 없다")
    void roundTrip_noDataLoss() {
        Payment original = Payment.create("order-1", "user-1", 40000L);
        original.confirm("pk_test_789", "CARD", "https://receipt.url");

        PaymentJpaEntity entity = mapper.toEntity(original);
        Payment restored = mapper.toDomain(entity);

        assertThat(restored.getPaymentId()).isEqualTo(original.getPaymentId());
        assertThat(restored.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(restored.getUserId()).isEqualTo(original.getUserId());
        assertThat(restored.getAmount()).isEqualTo(original.getAmount());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getPaidAt()).isEqualTo(original.getPaidAt());
        assertThat(restored.getRefundedAt()).isEqualTo(original.getRefundedAt());
        assertThat(restored.getPaymentKey()).isEqualTo(original.getPaymentKey());
        assertThat(restored.getPaymentMethod()).isEqualTo(original.getPaymentMethod());
        assertThat(restored.getReceiptUrl()).isEqualTo(original.getReceiptUrl());
    }
}
