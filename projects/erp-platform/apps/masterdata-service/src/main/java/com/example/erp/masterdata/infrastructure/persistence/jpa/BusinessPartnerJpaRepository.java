package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessPartnerJpaRepository extends JpaRepository<BusinessPartner, String> {

    Optional<BusinessPartner> findByIdAndTenantId(String id, String tenantId);

    Optional<BusinessPartner> findByCodeAndTenantId(String code, String tenantId);

    List<BusinessPartner> findAllByTenantId(String tenantId, Pageable pageable);
}
