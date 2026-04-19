package com.wms.master.application.port.in;

import com.wms.master.application.command.CreateSkuCommand;
import com.wms.master.application.command.DeactivateSkuCommand;
import com.wms.master.application.command.ReactivateSkuCommand;
import com.wms.master.application.command.UpdateSkuCommand;
import com.wms.master.application.result.SkuResult;

/**
 * Inbound port for SKU write-side operations.
 */
public interface SkuCrudUseCase {

    SkuResult create(CreateSkuCommand command);

    SkuResult update(UpdateSkuCommand command);

    SkuResult deactivate(DeactivateSkuCommand command);

    SkuResult reactivate(ReactivateSkuCommand command);
}
