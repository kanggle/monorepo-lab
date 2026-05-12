package com.wms.master.application.port.in;

import com.wms.master.application.command.CreatePartnerCommand;
import com.wms.master.application.command.DeactivatePartnerCommand;
import com.wms.master.application.command.ReactivatePartnerCommand;
import com.wms.master.application.command.UpdatePartnerCommand;
import com.wms.master.application.result.PartnerResult;

/**
 * Inbound port for Partner write-side operations.
 */
public interface PartnerCrudUseCase {

    PartnerResult create(CreatePartnerCommand command);

    PartnerResult update(UpdatePartnerCommand command);

    PartnerResult deactivate(DeactivatePartnerCommand command);

    PartnerResult reactivate(ReactivatePartnerCommand command);
}
