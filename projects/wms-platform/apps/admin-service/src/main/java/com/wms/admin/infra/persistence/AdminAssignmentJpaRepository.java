package com.wms.admin.infra.persistence;

import com.wms.admin.domain.AssignmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAssignmentJpaRepository extends JpaRepository<AdminAssignmentJpaEntity, UUID> {

    @Query("""
            SELECT a FROM AdminAssignmentJpaEntity a
            WHERE a.userId = :userId AND a.roleId = :roleId
              AND ((:warehouseId IS NULL AND a.warehouseId IS NULL)
                   OR (a.warehouseId = :warehouseId))
              AND a.status = com.wms.admin.domain.AssignmentStatus.ACTIVE
            """)
    Optional<AdminAssignmentJpaEntity> findActiveTriple(@Param("userId") UUID userId,
                                                        @Param("roleId") UUID roleId,
                                                        @Param("warehouseId") UUID warehouseId);

    @Query("""
            SELECT a FROM AdminAssignmentJpaEntity a
            WHERE a.userId = :userId
              AND a.status = com.wms.admin.domain.AssignmentStatus.ACTIVE
            """)
    List<AdminAssignmentJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT a FROM AdminAssignmentJpaEntity a
            WHERE a.roleId = :roleId
              AND a.status = com.wms.admin.domain.AssignmentStatus.ACTIVE
            """)
    List<AdminAssignmentJpaEntity> findActiveByRoleId(@Param("roleId") UUID roleId);

    @Query("""
            SELECT COUNT(a) FROM AdminAssignmentJpaEntity a
            WHERE a.userId = :userId
              AND a.status = com.wms.admin.domain.AssignmentStatus.ACTIVE
            """)
    int countActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(a) FROM AdminAssignmentJpaEntity a
            WHERE a.roleId = :roleId
              AND a.status = com.wms.admin.domain.AssignmentStatus.ACTIVE
            """)
    int countActiveByRoleId(@Param("roleId") UUID roleId);

    @Query("""
            SELECT a FROM AdminAssignmentJpaEntity a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:roleId IS NULL OR a.roleId = :roleId)
              AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<AdminAssignmentJpaEntity> search(@Param("userId") UUID userId,
                                          @Param("roleId") UUID roleId,
                                          @Param("warehouseId") UUID warehouseId,
                                          @Param("status") AssignmentStatus status,
                                          Pageable pageable);
}
