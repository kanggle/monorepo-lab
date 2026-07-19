package com.example.scmplatform.demandplanning.adapter.outbound.persistence.adapter;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderSuggestionJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderSuggestionJpaRepository;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReorderSuggestionAdapter implements ReorderSuggestionPort {

    private static final List<SuggestionStatus> OPEN_STATUSES =
            List.of(SuggestionStatus.SUGGESTED, SuggestionStatus.APPROVED);

    private final ReorderSuggestionJpaRepository repository;

    @Override
    public Optional<ReorderSuggestion> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean hasOpenSuggestion(String tenantId, String skuCode, UUID warehouseId) {
        return repository.hasOpenSuggestion(tenantId, skuCode, warehouseId, OPEN_STATUSES);
    }

    @Override
    public ReorderSuggestion save(ReorderSuggestion suggestion) {
        ReorderSuggestionJpaEntity entity = repository.findById(suggestion.getId())
                .orElse(new ReorderSuggestionJpaEntity());
        entity.setId(suggestion.getId());
        entity.setSkuCode(suggestion.getSkuCode());
        entity.setWarehouseId(suggestion.getWarehouseId());
        entity.setWarehouseCode(suggestion.getWarehouseCode());
        entity.setSupplierId(suggestion.getSupplierId());
        entity.setSuggestedQty(suggestion.getSuggestedQty());
        entity.setStatus(suggestion.getStatus());
        entity.setSource(suggestion.getSource());
        entity.setTriggerEventId(suggestion.getTriggerEventId());
        entity.setTriggerAvailableQty(suggestion.getTriggerAvailableQty());
        entity.setMaterializedPoId(suggestion.getMaterializedPoId());
        entity.setTenantId(suggestion.getTenantId());
        entity.setVersion(suggestion.getVersion());
        entity.setCreatedAt(suggestion.getCreatedAt());
        entity.setUpdatedAt(suggestion.getUpdatedAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public Page<ReorderSuggestion> findAll(String tenantId, SuggestionStatus status,
                                            String skuCode, Pageable pageable) {
        Page<ReorderSuggestionJpaEntity> page;
        if (status != null && skuCode != null && !skuCode.isBlank()) {
            page = repository.findByTenantIdAndStatusAndSkuCode(tenantId, status, skuCode, pageable);
        } else if (status != null) {
            page = repository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (skuCode != null && !skuCode.isBlank()) {
            page = repository.findByTenantIdAndSkuCode(tenantId, skuCode, pageable);
        } else {
            page = repository.findByTenantId(tenantId, pageable);
        }
        return page.map(this::toDomain);
    }

    private ReorderSuggestion toDomain(ReorderSuggestionJpaEntity e) {
        return ReorderSuggestion.reconstitute(
                e.getId(), e.getSkuCode(), e.getWarehouseId(), e.getWarehouseCode(), e.getSupplierId(),
                e.getSuggestedQty(), e.getStatus(), e.getSource(),
                e.getTriggerEventId(), e.getTriggerAvailableQty(),
                e.getMaterializedPoId(), e.getTenantId(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
