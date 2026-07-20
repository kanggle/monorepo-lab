package com.example.payment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for {@code payment_refund_request} (TASK-BE-535). */
interface RefundRequestJpaRepository extends JpaRepository<RefundRequestJpaEntity, Long> {

    Optional<RefundRequestJpaEntity> findByPaymentIdAndIdempotencyKey(
            String paymentId, String idempotencyKey);
}
