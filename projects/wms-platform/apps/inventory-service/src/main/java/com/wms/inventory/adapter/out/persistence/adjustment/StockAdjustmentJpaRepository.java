package com.wms.inventory.adapter.out.persistence.adjustment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentJpaRepository extends JpaRepository<StockAdjustmentJpaEntity, UUID> {
}
