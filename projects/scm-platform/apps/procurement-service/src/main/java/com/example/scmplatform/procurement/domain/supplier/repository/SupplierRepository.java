package com.example.scmplatform.procurement.domain.supplier.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(String id, String tenantId);

    /**
     * Batch lookup by id inside one tenant (TASK-MONO-677 — resolves a PO page's
     * supplier references without a per-row query). Unknown ids are simply absent
     * from the result; an empty input returns an empty list without querying.
     */
    List<Supplier> findAllByIds(Collection<String> ids, String tenantId);

    /** Batch lookup by the tenant-scoped natural key; same contract as {@link #findAllByIds}. */
    List<Supplier> findAllByCodes(Collection<String> codes, String tenantId);

    /**
     * Lookup by the tenant-scoped natural key (V6 {@code ux_suppliers_tenant_code}).
     * This is what makes registration idempotent — see
     * {@code procurement-api.md} § POST /api/procurement/suppliers.
     */
    Optional<Supplier> findByCode(String code, String tenantId);

    PageResult<Supplier> search(String tenantId, String code, SupplierStatus status,
                                PageQuery pageQuery);
}
