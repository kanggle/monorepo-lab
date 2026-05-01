package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.MasterReadModelPort;
import com.wms.outbound.domain.model.masterref.LocationSnapshot;
import com.wms.outbound.domain.model.masterref.LotSnapshot;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
import com.wms.outbound.domain.model.masterref.ZoneSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeMasterReadModelPort implements MasterReadModelPort {

    private final Map<UUID, PartnerSnapshot> partners = new HashMap<>();
    private final Map<String, PartnerSnapshot> partnersByCode = new HashMap<>();
    private final Map<UUID, SkuSnapshot> skus = new HashMap<>();
    private final Map<String, SkuSnapshot> skusByCode = new HashMap<>();
    private final Map<UUID, WarehouseSnapshot> warehouses = new HashMap<>();
    private final Map<String, WarehouseSnapshot> warehousesByCode = new HashMap<>();
    private final Map<UUID, LotSnapshot> lots = new HashMap<>();

    public PartnerSnapshot addPartner(UUID id, String code,
                                      PartnerSnapshot.PartnerType type,
                                      PartnerSnapshot.Status status) {
        PartnerSnapshot s = new PartnerSnapshot(id, code, type, status, Instant.EPOCH, 1L);
        partners.put(id, s);
        partnersByCode.put(code, s);
        return s;
    }

    public SkuSnapshot addSku(UUID id, String code,
                              SkuSnapshot.TrackingType trackingType,
                              SkuSnapshot.Status status) {
        SkuSnapshot s = new SkuSnapshot(id, code, trackingType, status, Instant.EPOCH, 1L);
        skus.put(id, s);
        skusByCode.put(code, s);
        return s;
    }

    public WarehouseSnapshot addWarehouse(UUID id, String code,
                                          WarehouseSnapshot.Status status) {
        WarehouseSnapshot s = new WarehouseSnapshot(id, code, status, Instant.EPOCH, 1L);
        warehouses.put(id, s);
        warehousesByCode.put(code, s);
        return s;
    }

    @Override
    public Optional<WarehouseSnapshot> findWarehouse(UUID id) {
        return Optional.ofNullable(warehouses.get(id));
    }

    @Override
    public Optional<WarehouseSnapshot> findWarehouseByCode(String warehouseCode) {
        return Optional.ofNullable(warehousesByCode.get(warehouseCode));
    }

    @Override
    public Optional<ZoneSnapshot> findZone(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<LocationSnapshot> findLocation(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<SkuSnapshot> findSku(UUID id) {
        return Optional.ofNullable(skus.get(id));
    }

    @Override
    public Optional<SkuSnapshot> findSkuByCode(String skuCode) {
        return Optional.ofNullable(skusByCode.get(skuCode));
    }

    @Override
    public Optional<LotSnapshot> findLot(UUID id) {
        return Optional.ofNullable(lots.get(id));
    }

    @Override
    public Optional<LotSnapshot> findLotBySkuAndLotNo(UUID skuId, String lotNo) {
        return Optional.empty();
    }

    @Override
    public Optional<PartnerSnapshot> findPartner(UUID id) {
        return Optional.ofNullable(partners.get(id));
    }

    @Override
    public Optional<PartnerSnapshot> findPartnerByCode(String partnerCode) {
        return Optional.ofNullable(partnersByCode.get(partnerCode));
    }
}
