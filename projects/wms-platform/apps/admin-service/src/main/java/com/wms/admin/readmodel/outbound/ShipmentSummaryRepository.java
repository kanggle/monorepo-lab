package com.wms.admin.readmodel.outbound;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentSummaryRepository extends JpaRepository<ShipmentSummaryEntity, UUID> {

    @Query("SELECT s FROM ShipmentSummaryEntity s "
            + "WHERE (:warehouseId IS NULL OR s.warehouseId = :warehouseId) "
            + "AND (:orderId IS NULL OR s.orderId = :orderId) "
            + "AND (:carrierCode IS NULL OR s.carrierCode = :carrierCode)")
    Page<ShipmentSummaryEntity> search(@Param("warehouseId") UUID warehouseId,
                                       @Param("orderId") UUID orderId,
                                       @Param("carrierCode") String carrierCode,
                                       Pageable pageable);
}
