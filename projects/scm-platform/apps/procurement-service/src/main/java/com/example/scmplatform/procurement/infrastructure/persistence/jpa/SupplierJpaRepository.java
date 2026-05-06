package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.supplier.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierJpaRepository extends JpaRepository<Supplier, String> {

    Optional<Supplier> findByIdAndTenantId(String id, String tenantId);
}
