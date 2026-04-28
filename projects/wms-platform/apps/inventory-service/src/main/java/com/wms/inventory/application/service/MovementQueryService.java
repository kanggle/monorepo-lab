package com.wms.inventory.application.service;

import com.wms.inventory.application.port.in.MovementQueryUseCase;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovementQueryService implements MovementQueryUseCase {

    private final InventoryMovementRepository repository;

    public MovementQueryService(InventoryMovementRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<MovementView> list(MovementListCriteria criteria) {
        return repository.list(criteria);
    }
}
