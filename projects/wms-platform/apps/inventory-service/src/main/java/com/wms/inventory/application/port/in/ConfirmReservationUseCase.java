package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.ConfirmReservationCommand;
import com.wms.inventory.application.result.ReservationView;

public interface ConfirmReservationUseCase {

    ReservationView confirm(ConfirmReservationCommand command);
}
