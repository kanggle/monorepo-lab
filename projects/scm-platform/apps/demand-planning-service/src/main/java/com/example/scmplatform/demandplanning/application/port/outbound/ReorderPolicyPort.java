package com.example.scmplatform.demandplanning.application.port.outbound;

import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;

import java.util.Optional;

/**
 * Outbound port for reorder policy persistence.
 */
public interface ReorderPolicyPort {

    Optional<ReorderPolicy> findBySkuCode(String tenantId, String skuCode);

    ReorderPolicy save(ReorderPolicy policy);
}
