package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SupplierRepositoryAdapter implements SupplierRepository {

    private final SupplierJpaRepository jpa;

    @Override
    public Supplier save(Supplier supplier) {
        return jpa.save(supplier);
    }

    @Override
    public Optional<Supplier> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }
}
