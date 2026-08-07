package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.domain.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SupplierRepositoryImpl implements SupplierRepository {

    private final SupplierJpaRepository jpa;

    @Override
    public Supplier save(Supplier supplier) {
        return jpa.save(supplier);
    }

    @Override
    public Optional<Supplier> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<Supplier> findByCode(String code, String tenantId) {
        return jpa.findByCodeAndTenantId(code, tenantId);
    }

    @Override
    public PageResult<Supplier> search(String tenantId, String code, SupplierStatus status,
                                       PageQuery pageQuery) {
        Page<Supplier> page = jpa.search(tenantId, code, status, PageRequests.toPageable(pageQuery));
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
