package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter for {@link SettlementPeriod} — the aggregate is itself a JPA
 * entity, so this is a thin delegate over {@link SettlementPeriodJpaRepository} that
 * enforces tenant scoping at the chokepoint (load/list derive on {@code tenantId}).
 */
@Repository
@RequiredArgsConstructor
public class SettlementPeriodRepositoryImpl implements SettlementPeriodRepository {

    private final SettlementPeriodJpaRepository jpaRepository;

    @Override
    public SettlementPeriod save(SettlementPeriod period) {
        return jpaRepository.save(period);
    }

    @Override
    public Optional<SettlementPeriod> findById(String periodId, String tenantId) {
        return jpaRepository.findByPeriodIdAndTenantId(periodId, tenantId);
    }

    @Override
    public List<SettlementPeriod> findAll(String tenantId) {
        return jpaRepository.findByTenantIdOrderByToDesc(tenantId);
    }
}
