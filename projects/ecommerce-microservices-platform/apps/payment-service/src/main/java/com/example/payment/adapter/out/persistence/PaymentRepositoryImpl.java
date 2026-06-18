package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.Payment;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = mapper.toEntity(payment);
        PaymentJpaEntity saved = jpaRepository.save(entity);
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
}
