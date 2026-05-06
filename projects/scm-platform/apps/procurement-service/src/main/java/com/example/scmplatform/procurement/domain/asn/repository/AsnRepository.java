package com.example.scmplatform.procurement.domain.asn.repository;

import com.example.scmplatform.procurement.domain.asn.AdvanceShipmentNotice;

import java.util.Optional;

public interface AsnRepository {

    AdvanceShipmentNotice save(AdvanceShipmentNotice asn);

    Optional<AdvanceShipmentNotice> findById(String id, String tenantId);

    /**
     * Idempotency lookup (S2): a webhook retry presents the same
     * {@code supplierAsnRef} and the system returns the previously-persisted
     * ASN instead of creating a duplicate.
     */
    Optional<AdvanceShipmentNotice> findBySupplierAsnRef(String supplierAsnRef, String tenantId);
}
