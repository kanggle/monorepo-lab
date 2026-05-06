package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeStalenessJpaRepository extends JpaRepository<NodeStalenessJpaEntity, String> {

    List<NodeStalenessJpaEntity> findAllByTenantId(String tenantId);
}
