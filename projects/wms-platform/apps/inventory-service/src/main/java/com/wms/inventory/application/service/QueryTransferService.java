package com.wms.inventory.application.service;

import com.wms.inventory.application.port.in.QueryTransferUseCase;
import com.wms.inventory.application.port.out.StockTransferRepository;
import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.TransferView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryTransferService implements QueryTransferUseCase {

    private final StockTransferRepository repository;

    public QueryTransferService(StockTransferRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransferView> findById(UUID id) {
        return repository.findById(id).map(TransferView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<TransferView> list(TransferListCriteria criteria) {
        return repository.list(criteria);
    }
}
