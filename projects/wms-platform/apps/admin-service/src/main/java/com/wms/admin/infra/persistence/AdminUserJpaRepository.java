package com.wms.admin.infra.persistence;

import com.wms.admin.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminUserJpaRepository extends JpaRepository<AdminUserJpaEntity, UUID> {

    Optional<AdminUserJpaEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserCode(String userCode);

    @Query("""
            SELECT u FROM AdminUserJpaEntity u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:warehouseId IS NULL OR u.defaultWarehouseId = :warehouseId)
              AND (:q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(u.userCode) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<AdminUserJpaEntity> search(@Param("status") UserStatus status,
                                    @Param("warehouseId") UUID warehouseId,
                                    @Param("q") String q,
                                    Pageable pageable);
}
