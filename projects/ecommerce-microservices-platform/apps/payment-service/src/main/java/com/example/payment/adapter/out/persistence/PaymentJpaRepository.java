package com.example.payment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, String> {

    /** Tenant-scoped lookup for all read paths post-TASK-BE-400 (M2 layer 3, M3). */
    Optional<PaymentJpaEntity> findByOrderIdAndTenantId(String orderId, String tenantId);

    /** Tenant-scoped lookup by payment id (M2 layer 3, M3). */
    Optional<PaymentJpaEntity> findByPaymentIdAndTenantId(String paymentId, String tenantId);
}
