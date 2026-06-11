package com.example.scmplatform.demandplanning.application.port.outbound;

import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;

import java.util.Optional;

/**
 * Outbound port for SKU→supplier mapping persistence.
 */
public interface SkuSupplierMappingPort {

    Optional<SkuSupplierMapping> findBySkuCode(String tenantId, String skuCode);

    SkuSupplierMapping save(SkuSupplierMapping mapping);
}
