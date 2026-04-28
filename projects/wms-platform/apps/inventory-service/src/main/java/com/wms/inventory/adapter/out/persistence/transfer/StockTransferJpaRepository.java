package com.wms.inventory.adapter.out.persistence.transfer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferJpaRepository extends JpaRepository<StockTransferJpaEntity, UUID> {
}
