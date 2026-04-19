package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaSkuRepository extends JpaRepository<SkuJpaEntity, UUID> {

    boolean existsBySkuCode(String skuCode);

    boolean existsByBarcode(String barcode);

    /**
     * Lookup by sku_code. The caller uppercases the input — storage is always
     * UPPERCASE, guarded by a CHECK constraint in the V5 migration.
     */
    Optional<SkuJpaEntity> findBySkuCode(String skuCode);

    Optional<SkuJpaEntity> findByBarcode(String barcode);

    /**
     * Filter by optional status, optional trackingType, optional baseUom,
     * optional exact barcode, and optional case-insensitive substring against
     * {@code name} and {@code sku_code}. Null parameters mean "do not filter".
     */
    @Query("""
            SELECT s FROM SkuJpaEntity s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:trackingType IS NULL OR s.trackingType = :trackingType)
              AND (:baseUom IS NULL OR s.baseUom = :baseUom)
              AND (:barcode IS NULL OR s.barcode = :barcode)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(s.name)    LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.skuCode) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<SkuJpaEntity> search(
            @Param("status") WarehouseStatus status,
            @Param("trackingType") TrackingType trackingType,
            @Param("baseUom") BaseUom baseUom,
            @Param("barcode") String barcode,
            @Param("q") String q,
            Pageable pageable);
}
