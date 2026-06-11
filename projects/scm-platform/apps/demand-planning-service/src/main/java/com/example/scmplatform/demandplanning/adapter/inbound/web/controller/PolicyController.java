package com.example.scmplatform.demandplanning.adapter.inbound.web.controller;

import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.MappingRequest;
import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.MappingResponse;
import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.PolicyRequest;
import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.PolicyResponse;
import com.example.scmplatform.demandplanning.application.usecase.PolicyManagementUseCase;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for reorder policy and SKU-supplier mapping seed endpoints.
 * Base path: /api/demand-planning/policies and /api/demand-planning/sku-supplier-map.
 */
@RestController
@RequestMapping("/api/demand-planning")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyManagementUseCase policyManagementUseCase;

    @GetMapping("/policies/{skuCode}")
    public ResponseEntity<ApiEnvelope<PolicyResponse>> getPolicy(@PathVariable String skuCode) {
        ReorderPolicy policy = policyManagementUseCase.getPolicy(skuCode);
        return ResponseEntity.ok(ApiEnvelope.of(PolicyResponse.from(policy)));
    }

    @PutMapping("/policies/{skuCode}")
    public ResponseEntity<ApiEnvelope<PolicyResponse>> upsertPolicy(
            @PathVariable String skuCode,
            @Valid @RequestBody PolicyRequest request) {
        ReorderPolicy policy = policyManagementUseCase.upsertPolicy(
                skuCode, request.reorderPoint(), request.safetyStock(), request.reorderQty());
        return ResponseEntity.ok(ApiEnvelope.of(PolicyResponse.from(policy)));
    }

    @GetMapping("/sku-supplier-map/{skuCode}")
    public ResponseEntity<ApiEnvelope<MappingResponse>> getMapping(@PathVariable String skuCode) {
        SkuSupplierMapping mapping = policyManagementUseCase.getMapping(skuCode);
        return ResponseEntity.ok(ApiEnvelope.of(MappingResponse.from(mapping)));
    }

    @PutMapping("/sku-supplier-map/{skuCode}")
    public ResponseEntity<ApiEnvelope<MappingResponse>> upsertMapping(
            @PathVariable String skuCode,
            @Valid @RequestBody MappingRequest request) {
        SkuSupplierMapping mapping = policyManagementUseCase.upsertMapping(
                skuCode, request.supplierId(), request.defaultOrderQty(),
                request.leadTimeDays(), request.currency());
        return ResponseEntity.ok(ApiEnvelope.of(MappingResponse.from(mapping)));
    }
}
