package com.wms.admin.infra.persistence;

import com.wms.admin.domain.RoleStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRoleJpaRepository extends JpaRepository<AdminRoleJpaEntity, UUID> {

    Optional<AdminRoleJpaEntity> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    @Query("""
            SELECT r FROM AdminRoleJpaEntity r
            WHERE (:status IS NULL OR r.status = :status)
            """)
    Page<AdminRoleJpaEntity> search(@Param("status") RoleStatus status, Pageable pageable);
}
