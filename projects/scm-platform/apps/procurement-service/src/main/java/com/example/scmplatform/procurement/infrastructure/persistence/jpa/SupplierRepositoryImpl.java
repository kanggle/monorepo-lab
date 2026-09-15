package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.domain.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
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
    public List<Supplier> findAllByIds(Collection<String> ids, String tenantId) {
        // An empty IN () is not portable SQL — short-circuit instead of querying.
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByTenantIdAndIdIn(tenantId, ids);
    }

    @Override
    public List<Supplier> findAllByCodes(Collection<String> codes, String tenantId) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return jpa.findByTenantIdAndCodeIn(tenantId, codes);
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
