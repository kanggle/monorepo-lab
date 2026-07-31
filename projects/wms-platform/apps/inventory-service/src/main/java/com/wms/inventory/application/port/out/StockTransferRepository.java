package com.wms.inventory.application.port.out;

import com.example.common.page.PageResult;
import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.domain.model.StockTransfer;
import java.util.Optional;
import java.util.UUID;

public interface StockTransferRepository {

    StockTransfer insert(StockTransfer transfer);

    Optional<StockTransfer> findById(UUID id);

    PageResult<TransferView> list(TransferListCriteria criteria);
}
