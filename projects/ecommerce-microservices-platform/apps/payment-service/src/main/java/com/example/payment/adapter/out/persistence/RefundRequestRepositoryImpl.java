package com.example.payment.adapter.out.persistence;

import com.example.payment.application.port.out.RefundRequestRepository;
import com.example.payment.domain.model.RefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefundRequestRepositoryImpl implements RefundRequestRepository {

    private final RefundRequestJpaRepository jpaRepository;

    @Override
    public Optional<RefundRequest> find(String paymentId, String idempotencyKey) {
        return jpaRepository.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey)
                .map(RefundRequestJpaEntity::toDomain);
    }

    @Override
    public RefundRequest insert(RefundRequest refundRequest) {
        // saveAndFlush, not save: the INSERT must reach Postgres inside the caller's
        // try-block so a UNIQUE (payment_id, idempotency_key) violation arrives as a
        // DataIntegrityViolationException it can translate to 409. A plain save() would
        // defer the INSERT to the commit-time flush, past the catch — and past the PG
        // call, which is exactly the double-payout this table exists to prevent.
        return jpaRepository.saveAndFlush(RefundRequestJpaEntity.fromDomain(refundRequest))
                .toDomain();
    }
}
