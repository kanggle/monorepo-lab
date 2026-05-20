package com.example.erp.masterdata.domain.businesspartner.repository;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;

import java.util.List;
import java.util.Optional;

public interface BusinessPartnerRepository {
    BusinessPartner save(BusinessPartner businessPartner);

    Optional<BusinessPartner> findById(String id, String tenantId);

    Optional<BusinessPartner> findByCode(String code, String tenantId);

    List<BusinessPartner> findAll(String tenantId, int page, int size);
}
