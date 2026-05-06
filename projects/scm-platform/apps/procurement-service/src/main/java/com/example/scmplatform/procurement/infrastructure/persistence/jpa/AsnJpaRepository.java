package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.asn.AdvanceShipmentNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AsnJpaRepository extends JpaRepository<AdvanceShipmentNotice, String> {

    Optional<AdvanceShipmentNotice> findByIdAndTenantId(String id, String tenantId);

    Optional<AdvanceShipmentNotice> findBySupplierAsnRefAndTenantId(String supplierAsnRef, String tenantId);
}
