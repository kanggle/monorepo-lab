package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboundExpectationJpaRepository extends JpaRepository<InboundExpectationJpaEntity, String> {

    boolean existsByTenantIdAndSourcePoNumberAndSkuAndNodeId(
            String tenantId, String sourcePoNumber, String sku, String nodeId);

    List<InboundExpectationJpaEntity> findByNodeIdAndSkuAndTenantIdAndStatus(
            String nodeId, String sku, String tenantId,
            InboundExpectationJpaEntity.ExpectationStatusJpa status);
}
