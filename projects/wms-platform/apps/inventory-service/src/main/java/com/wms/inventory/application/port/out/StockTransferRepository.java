package com.wms.inventory.application.port.out;

import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.domain.model.StockTransfer;
import java.util.Optional;
import java.util.UUID;

public interface StockTransferRepository {

    StockTransfer insert(StockTransfer transfer);

    Optional<StockTransfer> findById(UUID id);

    PageView<TransferView> list(TransferListCriteria criteria);
}
