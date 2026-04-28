package com.wms.inventory.application.service;

import com.wms.inventory.application.port.in.QueryInventoryUseCase;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.PageView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryInventoryService implements QueryInventoryUseCase {

    private final InventoryRepository repository;

    public QueryInventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<InventoryView> list(InventoryListCriteria criteria) {
        return repository.listViews(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryView> findById(UUID id) {
        return repository.findViewById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryView> findByKey(UUID locationId, UUID skuId, UUID lotId) {
        return repository.findViewByKey(locationId, skuId, lotId);
    }
}
