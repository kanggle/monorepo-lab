package com.example.scmplatform.procurement.domain.supplier.repository;

import com.example.scmplatform.procurement.domain.supplier.Supplier;

import java.util.Optional;

public interface SupplierRepository {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(String id, String tenantId);
}
