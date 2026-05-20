package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BusinessPartnerRepositoryAdapter implements BusinessPartnerRepository {

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
    public List<BusinessPartner> findAll(String tenantId, int page, int size) {
        return jpa.findAllByTenantId(tenantId, PageRequest.of(page, size));
    }
}
