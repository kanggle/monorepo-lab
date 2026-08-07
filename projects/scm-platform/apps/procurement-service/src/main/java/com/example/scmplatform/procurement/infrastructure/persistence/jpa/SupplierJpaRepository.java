package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierJpaRepository extends JpaRepository<Supplier, String> {

    Optional<Supplier> findByIdAndTenantId(String id, String tenantId);

    Optional<Supplier> findByCodeAndTenantId(String code, String tenantId);

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.tenantId = :tenantId
              AND (:code IS NULL OR s.code = :code)
              AND (:status IS NULL OR s.status = :status)
            ORDER BY s.createdAt DESC
            """)
    Page<Supplier> search(@Param("tenantId") String tenantId,
                          @Param("code") String code,
                          @Param("status") SupplierStatus status,
                          Pageable pageable);
}
