package com.wms.master.application.port.in;

import com.wms.master.application.command.CreateZoneCommand;
import com.wms.master.application.command.DeactivateZoneCommand;
import com.wms.master.application.command.ReactivateZoneCommand;
import com.wms.master.application.command.UpdateZoneCommand;
import com.wms.master.application.result.ZoneResult;

/**
 * Inbound port for Zone write-side operations.
 */
public interface ZoneCrudUseCase {

    ZoneResult create(CreateZoneCommand command);

    ZoneResult update(UpdateZoneCommand command);

    ZoneResult deactivate(DeactivateZoneCommand command);

    ZoneResult reactivate(ReactivateZoneCommand command);
}
