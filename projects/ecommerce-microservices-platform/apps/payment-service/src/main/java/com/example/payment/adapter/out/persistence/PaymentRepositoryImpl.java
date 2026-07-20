package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.Payment;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.domain.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = mapper.toEntity(payment);
        PaymentJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Synchronous INSERT so a {@code payments.order_id} unique violation is catchable by
     * the caller — see {@link PaymentRepository#saveAndFlush}.
     */
    @Override
    public Payment saveAndFlush(Payment payment) {
        PaymentJpaEntity entity = mapper.toEntity(payment);
        PaymentJpaEntity saved = jpaRepository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Tenant-scoped lookup by payment id (M2 layer 3, M3 404-over-403).
     * A payment belonging to a different tenant resolves to empty → 404.
     */
    @Override
    public Optional<Payment> findById(String paymentId) {
        return jpaRepository.findByPaymentIdAndTenantId(paymentId, TenantContext.currentTenant())
                .map(mapper::toDomain);
    }

    /**
     * Tenant-scoped lookup by order id (M2 layer 3, M3 404-over-403).
     * The idempotency / saga path uses this — a cross-tenant orderId yields empty.
     */
    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return jpaRepository.findByOrderIdAndTenantId(orderId, TenantContext.currentTenant())
                .map(mapper::toDomain);
    }

    /**
     * Fresh, persistence-context-bypassing lookup by order id (TASK-BE-443, money-safety).
     *
     * <p>The derived query locates the row (and returns the entity already managed in this
     * session if it was loaded earlier in the same transaction). {@code entityManager.refresh}
     * then forces a re-SELECT against the database and overwrites the managed entity's stale L1
     * field values with the committed columns — so a concurrently-committed {@code VOIDED}
     * transition (an {@code OrderCancelled} that committed on a separate connection during the
     * slow PG capture) is actually observed by {@code PaymentConfirmService}'s post-capture
     * re-read, instead of being masked by Hibernate's session-level managed-entity identity.
     *
     * <p>If the row is not present this returns empty (payments are never hard-deleted in this
     * domain — cancellation is a {@code VOIDED} status transition, not a row removal — so the
     * refresh of a freshly-located managed entity cannot race into an {@code EntityNotFound}).
     */
    @Override
    public Optional<Payment> findByOrderIdFresh(String orderId) {
        return jpaRepository.findByOrderIdAndTenantId(orderId, TenantContext.currentTenant())
                .map(entity -> {
                    entityManager.refresh(entity);
                    return mapper.toDomain(entity);
                });
    }

    /**
     * Deliberately global — see {@link PaymentRepository#existsByOrderIdAcrossTenants}.
     */
    @Override
    public boolean existsByOrderIdAcrossTenants(String orderId) {
        return jpaRepository.existsByOrderId(orderId);
    }
}
