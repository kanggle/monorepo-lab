package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.period.SettlementPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for the {@link SettlementPeriod} aggregate. Tenant scoping
 * is applied by deriving on {@code (periodId, tenantId)} so a cross-tenant id resolves
 * to {@code empty} (→ 404, M3).
 */
public interface SettlementPeriodJpaRepository extends JpaRepository<SettlementPeriod, String> {

    Optional<SettlementPeriod> findByPeriodIdAndTenantId(String periodId, String tenantId);

    List<SettlementPeriod> findByTenantIdOrderByToDesc(String tenantId);
}
