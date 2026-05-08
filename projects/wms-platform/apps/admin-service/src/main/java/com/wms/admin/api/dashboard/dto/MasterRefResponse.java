package com.wms.admin.api.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.wms.admin.readmodel.master.LocationRefEntity;
import com.wms.admin.readmodel.master.LotRefEntity;
import com.wms.admin.readmodel.master.PartnerRefEntity;
import com.wms.admin.readmodel.master.SkuRefEntity;
import com.wms.admin.readmodel.master.WarehouseRefEntity;
import com.wms.admin.readmodel.master.ZoneRefEntity;

/**
 * Generic response shape for {@code GET /dashboard/refs/{type}}. The exact
 * fields per {@code type} are inlined as a {@code Map<String, Object>} so a
 * single endpoint can serve all 6 master-ref kinds.
 */
public record MasterRefResponse(
        UUID id,
        String code,
        String name,
        String status,
        Instant lastEventAt,
        long version,
        Map<String, Object> attributes) {

    public static MasterRefResponse from(WarehouseRefEntity e) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("timezone", e.getTimezone());
        return new MasterRefResponse(e.getId(), e.getWarehouseCode(), e.getName(),
                e.getStatus(), e.getLastEventAt(), e.getVersion(), attrs);
    }

    public static MasterRefResponse from(ZoneRefEntity e) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("warehouseId", e.getWarehouseId());
        attrs.put("zoneType", e.getZoneType());
        return new MasterRefResponse(e.getId(), e.getZoneCode(), e.getName(),
                e.getStatus(), e.getLastEventAt(), e.getVersion(), attrs);
    }

    public static MasterRefResponse from(LocationRefEntity e) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("warehouseId", e.getWarehouseId());
        attrs.put("zoneId", e.getZoneId());
        attrs.put("locationType", e.getLocationType());
        return new MasterRefResponse(e.getId(), e.getLocationCode(), null,
                e.getStatus(), e.getLastEventAt(), e.getVersion(), attrs);
    }

    public static MasterRefResponse from(SkuRefEntity e) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("baseUom", e.getBaseUom());
        attrs.put("trackingType", e.getTrackingType());
        return new MasterRefResponse(e.getId(), e.getSkuCode(), e.getName(),
                e.getStatus(), e.getLastEventAt(), e.getVersion(), attrs);
    }

    public static MasterRefResponse from(LotRefEntity e) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("skuId", e.getSkuId());
        LocalDate expiry = e.getExpiryDate();
        if (expiry != null) attrs.put("expiryDate", expiry.toString());
        return new MasterRefResponse(e.getId(), e.getLotNo(), null,
                e.getStatus(), e.getLastEventAt(), e.getVersion(), attrs);
    }

    public static MasterRefResponse from(PartnerRefEntity e) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("partnerType", e.getPartnerType());
        return new MasterRefResponse(e.getId(), e.getPartnerCode(), e.getName(),
                e.getStatus(), e.getLastEventAt(), e.getVersion(), attrs);
    }
}
