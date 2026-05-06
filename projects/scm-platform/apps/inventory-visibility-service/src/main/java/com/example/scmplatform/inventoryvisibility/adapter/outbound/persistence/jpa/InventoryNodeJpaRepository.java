package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryNodeJpaRepository extends JpaRepository<InventoryNodeJpaEntity, String> {

    Optional<InventoryNodeJpaEntity> findByTenantIdAndNodeExternalId(String tenantId, String nodeExternalId);

    List<InventoryNodeJpaEntity> findAllByTenantId(String tenantId);
}
