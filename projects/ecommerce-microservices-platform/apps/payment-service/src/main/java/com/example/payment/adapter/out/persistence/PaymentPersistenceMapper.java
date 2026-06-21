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
                entity.getTenantId(),
                entity.getAmount(),
                entity.getRefundedAmount(),
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
