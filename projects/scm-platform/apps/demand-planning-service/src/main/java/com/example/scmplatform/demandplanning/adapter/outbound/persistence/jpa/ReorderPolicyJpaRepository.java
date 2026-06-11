package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReorderPolicyJpaRepository
        extends JpaRepository<ReorderPolicyJpaEntity, ReorderPolicyJpaEntity.PK> {

    Optional<ReorderPolicyJpaEntity> findByTenantIdAndSkuCode(String tenantId, String skuCode);
}
