package com.wms.inventory.application.service;

import com.wms.inventory.application.port.in.QueryAdjustmentUseCase;
import com.wms.inventory.application.port.out.StockAdjustmentRepository;
import com.example.common.page.PageResult;
import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.result.AdjustmentView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryAdjustmentService implements QueryAdjustmentUseCase {

    private final StockAdjustmentRepository repository;

    public QueryAdjustmentService(StockAdjustmentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdjustmentView> findById(UUID id) {
        return repository.findById(id).map(AdjustmentView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AdjustmentView> list(AdjustmentListCriteria criteria) {
        return repository.list(criteria);
    }
}
