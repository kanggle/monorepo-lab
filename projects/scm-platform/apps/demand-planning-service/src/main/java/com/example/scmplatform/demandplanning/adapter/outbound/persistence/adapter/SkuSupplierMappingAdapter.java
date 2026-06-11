package com.example.scmplatform.demandplanning.adapter.outbound.persistence.adapter;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.SkuSupplierMappingJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.SkuSupplierMappingJpaRepository;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SkuSupplierMappingAdapter implements SkuSupplierMappingPort {

    private final SkuSupplierMappingJpaRepository repository;

    @Override
    public Optional<SkuSupplierMapping> findBySkuCode(String tenantId, String skuCode) {
        return repository.findByTenantIdAndSkuCode(tenantId, skuCode).map(this::toDomain);
    }

    @Override
    public SkuSupplierMapping save(SkuSupplierMapping mapping) {
        SkuSupplierMappingJpaEntity entity = repository.findByTenantIdAndSkuCode(
                mapping.getTenantId(), mapping.getSkuCode())
                .orElse(new SkuSupplierMappingJpaEntity());
        entity.setTenantId(mapping.getTenantId());
        entity.setSkuCode(mapping.getSkuCode());
        entity.setSupplierId(mapping.getSupplierId());
        entity.setDefaultOrderQty(mapping.getDefaultOrderQty());
        entity.setLeadTimeDays(mapping.getLeadTimeDays());
        entity.setCurrency(mapping.getCurrency());
        return toDomain(repository.save(entity));
    }

    private SkuSupplierMapping toDomain(SkuSupplierMappingJpaEntity e) {
        return new SkuSupplierMapping(e.getSkuCode(), e.getSupplierId(),
                e.getDefaultOrderQty(), e.getLeadTimeDays(), e.getCurrency(), e.getTenantId());
    }
}
