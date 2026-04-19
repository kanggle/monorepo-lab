package com.wms.master.domain.exception;

import java.util.UUID;

public class ZoneCodeDuplicateException extends MasterDomainException {

    public ZoneCodeDuplicateException(UUID warehouseId, String zoneCode) {
        super(
                "ZONE_CODE_DUPLICATE",
                "zoneCode '" + zoneCode + "' is already taken within warehouse " + warehouseId);
    }
}
