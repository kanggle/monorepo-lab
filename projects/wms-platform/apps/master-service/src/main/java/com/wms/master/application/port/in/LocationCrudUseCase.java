package com.wms.master.application.port.in;

import com.wms.master.application.command.CreateLocationCommand;
import com.wms.master.application.command.DeactivateLocationCommand;
import com.wms.master.application.command.ReactivateLocationCommand;
import com.wms.master.application.command.UpdateLocationCommand;
import com.wms.master.application.result.LocationResult;

/**
 * Inbound port for Location write-side operations.
 */
public interface LocationCrudUseCase {

    LocationResult create(CreateLocationCommand command);

    LocationResult update(UpdateLocationCommand command);

    LocationResult deactivate(DeactivateLocationCommand command);

    LocationResult reactivate(ReactivateLocationCommand command);
}
