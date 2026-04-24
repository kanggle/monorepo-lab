package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.Payment;
import org.springframework.stereotype.Component;

@Component
class PaymentPersistenceMapper {

    Payment toDomain(PaymentJpaEntity entity) {
        return Payment.reconstitute(
                entity.getPaymentId(),
                entity.getOrderId(),
                entity.getUserId(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getPaidAt(),
                entity.getRefundedAt(),
                entity.getPaymentKey(),
                entity.getPaymentMethod(),
                entity.getReceiptUrl()
        );
    }

    PaymentJpaEntity toEntity(Payment payment) {
        return PaymentJpaEntity.fromDomain(payment);
    }
}
