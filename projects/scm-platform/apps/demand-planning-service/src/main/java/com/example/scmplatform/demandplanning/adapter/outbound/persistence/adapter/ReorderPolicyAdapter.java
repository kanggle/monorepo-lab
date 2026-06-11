package com.example.scmplatform.demandplanning.adapter.outbound.persistence.adapter;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderPolicyJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderPolicyJpaRepository;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderPolicyPort;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReorderPolicyAdapter implements ReorderPolicyPort {

    private final ReorderPolicyJpaRepository repository;

    @Override
    public Optional<ReorderPolicy> findBySkuCode(String tenantId, String skuCode) {
        return repository.findByTenantIdAndSkuCode(tenantId, skuCode).map(this::toDomain);
    }

    @Override
    public ReorderPolicy save(ReorderPolicy policy) {
        ReorderPolicyJpaEntity entity = repository.findByTenantIdAndSkuCode(
                policy.getTenantId(), policy.getSkuCode())
                .orElse(new ReorderPolicyJpaEntity());
        entity.setTenantId(policy.getTenantId());
        entity.setSkuCode(policy.getSkuCode());
        entity.setReorderPoint(policy.getReorderPoint());
        entity.setSafetyStock(policy.getSafetyStock());
        entity.setReorderQty(policy.getReorderQty());
        entity.setUpdatedAt(Instant.now());
        return toDomain(repository.save(entity));
    }

    private ReorderPolicy toDomain(ReorderPolicyJpaEntity e) {
        return new ReorderPolicy(e.getSkuCode(), e.getReorderPoint(), e.getSafetyStock(),
                e.getReorderQty(), e.getTenantId(), e.getVersion(), e.getUpdatedAt());
    }
}
