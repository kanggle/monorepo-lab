package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.ReleaseReservationCommand;
import com.wms.inventory.application.result.ReservationView;

public interface ReleaseReservationUseCase {

    ReservationView release(ReleaseReservationCommand command);
}
