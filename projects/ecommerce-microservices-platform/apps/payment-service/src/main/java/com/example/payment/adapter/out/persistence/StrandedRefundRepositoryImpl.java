package com.example.payment.adapter.out.persistence;

import com.example.payment.application.port.out.StrandedRefundRepository;
import com.example.payment.domain.model.StrandedRefund;
import com.example.payment.domain.model.StrandedRefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StrandedRefundRepositoryImpl implements StrandedRefundRepository {

    private final StrandedRefundJpaRepository jpaRepository;

    @Override
    public StrandedRefund save(StrandedRefund strandedRefund) {
        return jpaRepository.save(StrandedRefundJpaEntity.fromDomain(strandedRefund)).toDomain();
    }

    @Override
    public Optional<StrandedRefund> findById(Long id) {
        return jpaRepository.findById(id).map(StrandedRefundJpaEntity::toDomain);
    }

    @Override
    public Optional<StrandedRefund> findOpenByPaymentId(String paymentId) {
        return jpaRepository.findFirstByPaymentIdAndStatus(paymentId, StrandedRefundStatus.STRANDED)
                .map(StrandedRefundJpaEntity::toDomain);
    }

    @Override
    public List<StrandedRefund> findDue(Instant now, int limit) {
        return jpaRepository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        StrandedRefundStatus.STRANDED, now, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(StrandedRefundJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countOpen() {
        return jpaRepository.countByStatus(StrandedRefundStatus.STRANDED);
    }
}
