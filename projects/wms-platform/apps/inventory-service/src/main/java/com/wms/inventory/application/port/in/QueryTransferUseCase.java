package com.wms.inventory.application.port.in;

import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.TransferView;
import java.util.Optional;
import java.util.UUID;

public interface QueryTransferUseCase {

    Optional<TransferView> findById(UUID id);

    PageView<TransferView> list(TransferListCriteria criteria);
}
