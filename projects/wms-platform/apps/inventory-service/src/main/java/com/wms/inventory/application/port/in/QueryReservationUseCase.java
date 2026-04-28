package com.wms.inventory.application.port.in;

import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.ReservationView;
import java.util.Optional;
import java.util.UUID;

public interface QueryReservationUseCase {

    Optional<ReservationView> findById(UUID id);

    PageView<ReservationView> list(ReservationListCriteria criteria);
}
