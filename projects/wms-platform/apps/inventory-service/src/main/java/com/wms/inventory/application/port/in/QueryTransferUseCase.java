package com.wms.inventory.application.port.in;

import com.example.common.page.PageResult;
import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.TransferView;
import java.util.Optional;
import java.util.UUID;

public interface QueryTransferUseCase {

    Optional<TransferView> findById(UUID id);

    PageResult<TransferView> list(TransferListCriteria criteria);
}
