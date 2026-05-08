package com.wms.admin.infra.persistence;

import com.wms.admin.domain.SettingScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminSettingJpaRepository
        extends JpaRepository<AdminSettingJpaEntity, AdminSettingId> {

    @Query("""
            SELECT s FROM AdminSettingJpaEntity s
            WHERE (:keyPrefix IS NULL OR s.key LIKE CONCAT(:keyPrefix, '%'))
              AND (:scope IS NULL OR s.scope = :scope)
              AND (:warehouseId IS NULL OR s.warehouseId = :warehouseId)
            """)
    Page<AdminSettingJpaEntity> search(@Param("keyPrefix") String keyPrefix,
                                       @Param("scope") SettingScope scope,
                                       @Param("warehouseId") java.util.UUID warehouseId,
                                       Pageable pageable);
}
