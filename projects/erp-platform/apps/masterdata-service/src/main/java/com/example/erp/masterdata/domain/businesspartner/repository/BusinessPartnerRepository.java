package com.example.erp.masterdata.domain.businesspartner.repository;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;

import java.util.Optional;

public interface BusinessPartnerRepository {
    BusinessPartner save(BusinessPartner businessPartner);

    Optional<BusinessPartner> findById(String id, String tenantId);

    Optional<BusinessPartner> findByCode(String code, String tenantId);

    /**
     * Filtered, paginated list with the TRUE total-row count
     * (masterdata-api.md § GET /business-partners + § PageMeta).
     */
    PageResult<BusinessPartner> findAll(String tenantId, BusinessPartnerListFilter filter, int page, int size);
}
