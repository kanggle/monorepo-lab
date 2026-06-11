package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkuSupplierMappingJpaRepository
        extends JpaRepository<SkuSupplierMappingJpaEntity, SkuSupplierMappingJpaEntity.PK> {

    Optional<SkuSupplierMappingJpaEntity> findByTenantIdAndSkuCode(String tenantId, String skuCode);
}
