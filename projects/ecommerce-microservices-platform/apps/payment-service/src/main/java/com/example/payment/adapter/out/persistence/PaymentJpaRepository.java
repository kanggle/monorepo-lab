package com.example.payment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, String> {

    /** Tenant-scoped lookup for all read paths post-TASK-BE-400 (M2 layer 3, M3). */
    Optional<PaymentJpaEntity> findByOrderIdAndTenantId(String orderId, String tenantId);

    /** Tenant-scoped lookup by payment id (M2 layer 3, M3). */
    Optional<PaymentJpaEntity> findByPaymentIdAndTenantId(String paymentId, String tenantId);

    /**
     * Deliberately NOT tenant-scoped (TASK-BE-543 AC-1). {@code order_id} is a
     * globally-unique key (assigned once in order-service via {@code UUID.randomUUID()} —
     * no tenant-partitioned generation scheme, see {@code Order.create}), so the DB
     * constraint on this column ({@code payments.order_id UNIQUE}, V1) is correctly
     * global. This method exists solely to detect a cross-tenant reference to an
     * {@code orderId} that already has a payment row under a DIFFERENT tenant — the
     * caller must reject with 404 (mask cross-tenant existence, M3) BEFORE attempting
     * an insert that would otherwise hit the global unique constraint. Never used to
     * decide idempotency/ownership by itself — {@link #findByOrderIdAndTenantId} still
     * owns that decision for the caller's own tenant.
     */
    boolean existsByOrderId(String orderId);
}
