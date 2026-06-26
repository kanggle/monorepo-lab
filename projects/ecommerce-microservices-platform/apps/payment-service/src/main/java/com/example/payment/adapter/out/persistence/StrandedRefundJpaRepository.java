package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.StrandedRefundStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface StrandedRefundJpaRepository extends JpaRepository<StrandedRefundJpaEntity, Long> {

    Optional<StrandedRefundJpaEntity> findFirstByPaymentIdAndStatus(String paymentId, StrandedRefundStatus status);

    List<StrandedRefundJpaEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            StrandedRefundStatus status, Instant cutoff, Pageable pageable);

    long countByStatus(StrandedRefundStatus status);
}
