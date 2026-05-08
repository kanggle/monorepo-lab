package com.wms.admin.readmodel.inbound;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsnSummaryRepository extends JpaRepository<AsnSummaryEntity, UUID> {

    @Query("SELECT a FROM AsnSummaryEntity a "
            + "WHERE (:warehouseId IS NULL OR a.warehouseId = :warehouseId) "
            + "AND (:supplierPartnerId IS NULL OR a.supplierPartnerId = :supplierPartnerId) "
            + "AND (:status IS NULL OR a.status = :status) "
            + "AND (:source IS NULL OR a.source = :source)")
    Page<AsnSummaryEntity> search(@Param("warehouseId") UUID warehouseId,
                                  @Param("supplierPartnerId") UUID supplierPartnerId,
                                  @Param("status") String status,
                                  @Param("source") String source,
                                  Pageable pageable);
}
