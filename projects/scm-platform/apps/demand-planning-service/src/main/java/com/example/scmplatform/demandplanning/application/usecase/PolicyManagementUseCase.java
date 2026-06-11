package com.example.scmplatform.demandplanning.application.usecase;

import com.example.scmplatform.demandplanning.application.port.outbound.ReorderPolicyPort;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.domain.error.MappingNotFoundException;
import com.example.scmplatform.demandplanning.domain.error.PolicyNotFoundException;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Seed and inspect reorder policy + SKU-supplier mapping (operator admin surface).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyManagementUseCase {

    static final String TENANT_ID = "scm";

    private final ReorderPolicyPort policyPort;
    private final SkuSupplierMappingPort mappingPort;

    @Transactional(readOnly = true)
    public ReorderPolicy getPolicy(String skuCode) {
        return policyPort.findBySkuCode(TENANT_ID, skuCode)
                .orElseThrow(() -> new PolicyNotFoundException(skuCode));
    }

    @Transactional
    public ReorderPolicy upsertPolicy(String skuCode, int reorderPoint, int safetyStock, int reorderQty) {
        ReorderPolicy policy = new ReorderPolicy(skuCode, reorderPoint, safetyStock,
                reorderQty, TENANT_ID, 0, Instant.now());
        ReorderPolicy saved = policyPort.save(policy);
        log.info("Reorder policy upserted: skuCode={} reorderPoint={} reorderQty={}",
                skuCode, reorderPoint, reorderQty);
        return saved;
    }

    @Transactional(readOnly = true)
    public SkuSupplierMapping getMapping(String skuCode) {
        return mappingPort.findBySkuCode(TENANT_ID, skuCode)
                .orElseThrow(() -> new MappingNotFoundException(skuCode));
    }

    @Transactional
    public SkuSupplierMapping upsertMapping(String skuCode, UUID supplierId, int defaultOrderQty,
                                             int leadTimeDays, String currency) {
        SkuSupplierMapping mapping = new SkuSupplierMapping(skuCode, supplierId, defaultOrderQty,
                leadTimeDays, currency, TENANT_ID);
        SkuSupplierMapping saved = mappingPort.save(mapping);
        log.info("SKU-supplier mapping upserted: skuCode={} supplierId={}", skuCode, supplierId);
        return saved;
    }
}
