package com.wms.inventory.adapter.out.persistence.movement;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementJpaRepository extends JpaRepository<InventoryMovementJpaEntity, UUID> {
}
