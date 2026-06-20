package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.payout.SellerPayout;
import com.example.settlement.domain.repository.SellerPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence adapter for {@link SellerPayout} — the aggregate is itself a JPA
 * entity, so this is a thin delegate over {@link SellerPayoutJpaRepository}.
 */
@Repository
@RequiredArgsConstructor
public class SellerPayoutRepositoryImpl implements SellerPayoutRepository {

    private final SellerPayoutJpaRepository jpaRepository;

    @Override
    public List<SellerPayout> saveAll(List<SellerPayout> payouts) {
        return jpaRepository.saveAll(payouts);
    }

    @Override
    public SellerPayout save(SellerPayout payout) {
        return jpaRepository.save(payout);
    }

    @Override
    public List<SellerPayout> findByPeriodAndTenant(String periodId, String tenantId) {
        return jpaRepository.findByPeriodIdAndTenantId(periodId, tenantId);
    }
}
