package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerListFilter;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BusinessPartnerRepositoryImpl implements BusinessPartnerRepository {

    private final BusinessPartnerJpaRepository jpa;

    @Override
    public BusinessPartner save(BusinessPartner businessPartner) {
        return jpa.save(businessPartner);
    }

    @Override
    public Optional<BusinessPartner> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<BusinessPartner> findByCode(String code, String tenantId) {
        return jpa.findByCodeAndTenantId(code, tenantId);
    }

    @Override
    public PageResult<BusinessPartner> findAll(String tenantId, BusinessPartnerListFilter filter, int page, int size) {
        MasterStatus status = MasterStatusFilters.toStatus(filter.active());
        List<BusinessPartner> content = jpa.findFiltered(tenantId, status, filter.partnerType(),
                filter.asOf(), PageRequest.of(page, size));
        long total = jpa.countFiltered(tenantId, status, filter.partnerType(), filter.asOf());
        return new PageResult<>(content, total);
    }
}
