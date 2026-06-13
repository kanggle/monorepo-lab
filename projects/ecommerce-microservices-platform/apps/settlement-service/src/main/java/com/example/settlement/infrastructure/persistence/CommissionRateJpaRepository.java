package com.example.settlement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommissionRateJpaRepository extends JpaRepository<CommissionRateJpaEntity, String> {

    Optional<CommissionRateJpaEntity> findByTenantIdAndSellerId(String tenantId, String sellerId);
}
