package com.wms.inventory.application.port.in;

import com.example.common.page.PageResult;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.ReservationView;
import java.util.Optional;
import java.util.UUID;

public interface QueryReservationUseCase {

    Optional<ReservationView> findById(UUID id);

    PageResult<ReservationView> list(ReservationListCriteria criteria);
}
